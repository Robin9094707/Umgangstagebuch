package app.umgangstagebuch.data

import android.content.Context
import android.net.Uri
import app.umgangstagebuch.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface RepoState {
    data object Loading : RepoState
    data object NeedsSetup : RepoState
    data object FolderLost : RepoState
    data object Ready : RepoState
}

class Repository(private val context: Context, val prefs: Prefs) {
    val storage = FolderStorage(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<RepoState>(RepoState.Loading)
    val state: StateFlow<RepoState> = _state.asStateFlow()

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _photos = MutableStateFlow<Map<String, Uri>>(emptyMap())
    val photos: StateFlow<Map<String, Uri>> = _photos.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val folder: Uri? get() = prefs.current.folderUri

    fun message(text: String) { _messages.tryEmit(text) }

    private val initMutex = Mutex()

    suspend fun init() = initMutex.withLock {
        if (_state.value != RepoState.Loading) return@withLock
        load()
    }

    /** Erneut laden (z. B. nach Rückkehr in die App). */
    suspend fun reload() = initMutex.withLock { load() }

    private suspend fun load() {
        val s = prefs.current
        val uri = s.folderUri
        when {
            uri == null || !s.onboardingDone -> _state.value = RepoState.NeedsSetup
            !storage.hasAccess(uri) -> _state.value = RepoState.FolderLost
            else -> runCatching {
                _data.value = storage.load(uri) ?: AppData()
                _photos.value = storage.photoIndex(uri)
                _state.value = RepoState.Ready
                Reminders.reschedule(context, _data.value, prefs.current)
            }.onFailure {
                _state.value = RepoState.FolderLost
                message("Daten konnten nicht geladen werden: ${it.message}")
            }
        }
    }

    /** Ordner übernehmen. Liefert vorhandene Daten zurück, falls dort schon ein Tagebuch liegt. */
    suspend fun connectFolder(uri: Uri): Result<AppData?> = runCatching {
        storage.takePermission(uri)
        val existing = if (storage.hasData(uri)) storage.load(uri) else null
        prefs.update { it.copy(folderUri = uri) }
        storage.releaseOtherPermissions(uri)
        existing
    }

    /** Nach verlorenem Zugriff denselben oder einen neuen Ordner wieder verbinden. */
    suspend fun reconnect(uri: Uri): Result<Unit> = runCatching {
        storage.takePermission(uri)
        prefs.update { it.copy(folderUri = uri) }
        storage.releaseOtherPermissions(uri)
        val existing = storage.load(uri)
        if (existing != null) {
            _data.value = existing
        } else {
            // Leerer Ordner: aktuelle Daten (falls im Speicher) dort sichern
            storage.save(uri, _data.value.copy(savedAt = System.currentTimeMillis()))
        }
        _photos.value = storage.photoIndex(uri)
        _state.value = RepoState.Ready
    }

    suspend fun completeSetup(data: AppData) {
        val uri = prefs.current.folderUri ?: return
        _data.value = data
        storage.save(uri, data.copy(savedAt = System.currentTimeMillis()))
        _photos.value = storage.photoIndex(uri)
        prefs.update { it.copy(onboardingDone = true) }
        _state.value = RepoState.Ready
        Reminders.reschedule(context, data, prefs.current)
    }

    /** Ordner wechseln: aktuelle Daten inkl. Fotos in den neuen Ordner kopieren. */
    suspend fun moveToFolder(newUri: Uri): Result<Unit> = runCatching {
        val old = prefs.current.folderUri
        storage.takePermission(newUri)
        if (storage.hasData(newUri)) error("Im gewählten Ordner liegt bereits ein Tagebuch. Bitte einen leeren Ordner wählen oder das Backup importieren.")
        val oldPhotos = _photos.value
        oldPhotos.forEach { (name, uri) ->
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@forEach
            storage.writePhoto(newUri, name, bytes, context.contentResolver.getType(uri) ?: "image/jpeg")
        }
        storage.save(newUri, _data.value.copy(savedAt = System.currentTimeMillis()))
        prefs.update { it.copy(folderUri = newUri) }
        if (old != null && old != newUri) storage.releaseOtherPermissions(newUri)
        _photos.value = storage.photoIndex(newUri)
    }

    private suspend fun mutate(block: (AppData) -> AppData): Boolean = mutex.withLock {
        val uri = prefs.current.folderUri ?: return false
        val before = _data.value
        val after = block(before).copy(savedAt = System.currentTimeMillis())
        _data.value = after
        return runCatching { storage.save(uri, after) }
            .onFailure {
                _data.value = before
                message("Speichern fehlgeschlagen: ${it.message ?: "Ordner nicht erreichbar"}")
                if (!storage.hasAccess(uri)) _state.value = RepoState.FolderLost
            }
            .onSuccess { Reminders.reschedule(context, after, prefs.current) }
            .isSuccess
    }

    fun launch(block: suspend Repository.() -> Unit) { scope.launch { block() } }

    suspend fun updateProfile(profile: Profile) = mutate { it.copy(profile = profile) }

    suspend fun saveVisits(visits: List<Visit>) = mutate { d ->
        val ids = visits.map { it.id }.toSet()
        d.copy(visits = (d.visits.filterNot { it.id in ids } + visits.map { it.copy(updatedAt = System.currentTimeMillis()) }).sortedBy { it.start })
    }

    suspend fun saveVisit(visit: Visit) = saveVisits(listOf(visit))

    suspend fun deleteVisit(id: String, deleteSeries: Boolean = false) = mutate { d ->
        val series = d.visits.firstOrNull { it.id == id }?.seriesId
        val now = System.currentTimeMillis()
        d.copy(
            visits = d.visits.filterNot {
                it.id == id || (deleteSeries && series != null && it.seriesId == series && it.start > now && it.status == VisitStatus.PLANNED)
            },
            entries = d.entries.map { if (it.visitId == id) it.copy(visitId = null) else it },
        )
    }

    /** Speichert einen Eintrag. Zeitstempel, Prüfsumme und Versionsverlauf werden automatisch gepflegt. */
    suspend fun saveEntry(entry: Entry): Entry {
        val now = System.currentTimeMillis()
        val existing = _data.value.entries.firstOrNull { it.id == entry.id }
        val result = if (existing == null) {
            val e = entry.copy(createdAt = now, updatedAt = now, revisions = emptyList())
            e.copy(hash = e.contentHash())
        } else {
            val candidate = entry.copy(createdAt = existing.createdAt, updatedAt = existing.updatedAt, revisions = existing.revisions)
            if (candidate.contentHash() == existing.contentHash()) {
                existing
            } else {
                val rev = Revision(editedAt = now, previousHash = existing.hash.ifEmpty { existing.contentHash() }, previousJson = existing.toCanonicalJson())
                val e = entry.copy(createdAt = existing.createdAt, updatedAt = now, revisions = existing.revisions + rev)
                e.copy(hash = e.contentHash())
            }
        }
        if (result !== existing) mutate { d -> d.copy(entries = (d.entries.filterNot { it.id == result.id } + result).sortedByDescending { it.eventDate }) }
        return result
    }

    suspend fun deleteEntry(id: String) {
        val uri = prefs.current.folderUri ?: return
        val entry = _data.value.entries.firstOrNull { it.id == id } ?: return
        if (mutate { d -> d.copy(entries = d.entries.filterNot { it.id == id }) }) {
            val stillUsed = _data.value.entries.flatMap { e -> e.photos.map { it.fileName } }.toSet()
            entry.photos.filterNot { it.fileName in stillUsed }.forEach { storage.deletePhoto(uri, it.fileName) }
            _photos.value = storage.photoIndex(uri)
        }
    }

    suspend fun addPhoto(source: Uri, entryId: String): Photo? {
        val uri = prefs.current.folderUri ?: return null
        return runCatching { storage.importPhoto(uri, source, entryId) }
            .onFailure { message("Foto konnte nicht übernommen werden: ${it.message}") }
            .getOrNull()
            ?.let { (photo, photoUri) ->
                _photos.value = _photos.value + (photo.fileName to photoUri)
                photo
            }
    }

    /** Fotos entfernen, die keinem Eintrag mehr zugeordnet sind (z. B. nach Abbruch). */
    suspend fun cleanupPhotos(candidates: Collection<String>) {
        val uri = prefs.current.folderUri ?: return
        val used = _data.value.entries.flatMap { e -> e.photos.map { it.fileName } }.toSet()
        candidates.filterNot { it in used }.forEach { storage.deletePhoto(uri, it) }
        _photos.value = storage.photoIndex(uri)
    }

    suspend fun replaceAll(newData: AppData) = mutate { newData }

    suspend fun refreshPhotos() {
        prefs.current.folderUri?.let { _photos.value = storage.photoIndex(it) }
    }

    /** Nur für Tests und Vorschauen: Daten direkt setzen, ohne Ordner. */
    fun seedForTest(data: AppData, state: RepoState = RepoState.Ready) {
        _data.value = data
        _state.value = state
    }

    fun rescheduleReminders() = Reminders.reschedule(context, _data.value, prefs.current)
}
