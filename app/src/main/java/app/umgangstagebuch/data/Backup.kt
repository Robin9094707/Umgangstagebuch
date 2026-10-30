package app.umgangstagebuch.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupPreview(
    val data: AppData,
    val source: Uri,
    val photoCount: Int,
) {
    val entryCount get() = data.entries.size
    val visitCount get() = data.visits.size
}

enum class ImportMode { MERGE, REPLACE }

/** Export/Import als ZIP (umgangstagebuch.json + fotos/). Reine JSON-Dateien werden ebenfalls akzeptiert. */
class Backup(private val context: Context, private val repo: Repository) {

    private val resolver get() = context.contentResolver

    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        val data = repo.data.value
        val photos = repo.photos.value
        resolver.openOutputStream(target, "wt")?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(FolderStorage.DATA_FILE))
                zip.write(AppJson.encodeToString(AppData.serializer(), data).toByteArray())
                zip.closeEntry()
                val needed = data.entries.flatMap { e -> e.photos.map { it.fileName } }.toSet()
                photos.filterKeys { it in needed }.forEach { (name, uri) ->
                    resolver.openInputStream(uri)?.use { input ->
                        zip.putNextEntry(ZipEntry("${FolderStorage.PHOTO_DIR}/$name"))
                        input.copyTo(zip)
                        zip.closeEntry()
                    }
                }
            }
        } ?: throw IOException("Zieldatei konnte nicht geschrieben werden")
    }

    private fun isZip(source: Uri): Boolean =
        resolver.openInputStream(source)?.use { val h = ByteArray(2); it.read(h) == 2 && h[0] == 0x50.toByte() && h[1] == 0x4B.toByte() } == true

    /** Liest nur die Daten und zählt die Fotos – Fotos werden erst beim Übernehmen gestreamt. */
    suspend fun read(source: Uri): BackupPreview = withContext(Dispatchers.IO) {
        if (!isZip(source)) {
            val text = resolver.openInputStream(source)?.use { it.readBytes().decodeToString() }
                ?: throw IOException("Datei konnte nicht gelesen werden")
            return@withContext BackupPreview(AppJson.decodeFromString(AppData.serializer(), text), source, 0)
        }
        var data: AppData? = null
        var photos = 0
        resolver.openInputStream(source)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    when {
                        e.isDirectory -> Unit
                        e.name.endsWith(FolderStorage.DATA_FILE) ->
                            data = AppJson.decodeFromString(AppData.serializer(), zip.readBytes().decodeToString())
                        e.name.startsWith("${FolderStorage.PHOTO_DIR}/") -> photos++
                    }
                }
            }
        } ?: throw IOException("Datei konnte nicht gelesen werden")
        BackupPreview(data ?: throw IOException("Keine Tagebuch-Daten in der Sicherung gefunden"), source, photos)
    }

    suspend fun apply(preview: BackupPreview, mode: ImportMode) {
        val tree = repo.folder ?: throw IOException("Kein Speicherordner gewählt")
        repo.storage.namedSnapshot(tree, "vor-import-${app.umgangstagebuch.util.Fmt.fileStamp()}.json", repo.data.value)
        if (preview.photoCount > 0) withContext(Dispatchers.IO) {
            resolver.openInputStream(preview.source)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        val name = e.name.substringAfterLast('/')
                        if (e.isDirectory || name.isBlank() || name.contains("..") || !e.name.startsWith("${FolderStorage.PHOTO_DIR}/")) continue
                        val mime = when (name.substringAfterLast('.').lowercase()) {
                            "png" -> "image/png"; "heic" -> "image/heic"; "webp" -> "image/webp"; else -> "image/jpeg"
                        }
                        repo.storage.writePhoto(tree, name, zip.readBytes(), mime)
                    }
                }
            }
        }
        val merged = when (mode) {
            ImportMode.REPLACE -> preview.data
            ImportMode.MERGE -> merge(repo.data.value, preview.data)
        }
        repo.replaceAll(merged)
        repo.refreshPhotos()
    }

    private fun merge(a: AppData, b: AppData): AppData {
        val visits = (a.visits + b.visits).groupBy { it.id }.map { (_, v) -> v.maxBy { it.updatedAt } }.sortedBy { it.start }
        val entries = (a.entries + b.entries).groupBy { it.id }.map { (_, v) -> v.maxBy { it.updatedAt } }
            .sortedByDescending { it.eventDate }
        val profile = if (a.profile.isComplete) a.profile else b.profile
        return a.copy(profile = profile, visits = visits, entries = entries)
    }
}
