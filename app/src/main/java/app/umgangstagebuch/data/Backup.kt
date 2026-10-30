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
    val photos: Map<String, ByteArray>,
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

    suspend fun read(source: Uri): BackupPreview = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(source)?.use { it.readBytes() }
            ?: throw IOException("Datei konnte nicht gelesen werden")
        val isZip = bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        if (!isZip) {
            val data = AppJson.decodeFromString(AppData.serializer(), bytes.decodeToString())
            return@withContext BackupPreview(data, emptyMap())
        }
        var data: AppData? = null
        val photos = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.substringAfterLast('/')
                when {
                    e.isDirectory || name.isBlank() || name.contains("..") -> Unit
                    e.name.endsWith(FolderStorage.DATA_FILE) ->
                        data = AppJson.decodeFromString(AppData.serializer(), zip.readBytes().decodeToString())
                    e.name.startsWith("${FolderStorage.PHOTO_DIR}/") -> photos[name] = zip.readBytes()
                }
            }
        }
        BackupPreview(data ?: throw IOException("Keine Tagebuch-Daten in der Sicherung gefunden"), photos)
    }

    suspend fun apply(preview: BackupPreview, mode: ImportMode) {
        val tree = repo.folder ?: throw IOException("Kein Speicherordner gewählt")
        repo.storage.namedSnapshot(tree, "vor-import-${app.umgangstagebuch.util.Fmt.fileStamp()}.json", repo.data.value)
        withContext(Dispatchers.IO) {
            preview.photos.forEach { (name, bytes) ->
                val mime = when (name.substringAfterLast('.').lowercase()) {
                    "png" -> "image/png"; "heic" -> "image/heic"; "webp" -> "image/webp"; else -> "image/jpeg"
                }
                repo.storage.writePhoto(tree, name, bytes, mime)
            }
        }
        val current = repo.data.value
        val merged = when (mode) {
            ImportMode.REPLACE -> preview.data
            ImportMode.MERGE -> merge(current, preview.data)
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
