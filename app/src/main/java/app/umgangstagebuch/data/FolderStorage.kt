package app.umgangstagebuch.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/**
 * Speichert alles in einem vom Nutzer gewählten Ordner (Storage Access Framework).
 * Dadurch bleiben die Daten erhalten, auch wenn die App gelöscht wird.
 *
 * Aufbau:
 *  umgangstagebuch.json   – alle Daten
 *  fotos/                 – Fotos zu Einträgen
 *  sicherungen/           – automatische tägliche Sicherungen
 */
class FolderStorage(private val context: Context) {

    companion object {
        const val DATA_FILE = "umgangstagebuch.json"
        const val PHOTO_DIR = "fotos"
        const val SNAPSHOT_DIR = "sicherungen"
        private const val KEEP_SNAPSHOTS = 30
    }

    private val resolver get() = context.contentResolver

    fun takePermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(uri, flags)
    }

    fun releaseOtherPermissions(keep: Uri) {
        resolver.persistedUriPermissions.filter { it.uri != keep }.forEach {
            runCatching {
                resolver.releasePersistableUriPermission(
                    it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
    }

    fun hasAccess(uri: Uri): Boolean {
        val granted = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
        if (!granted) return false
        return runCatching { DocumentFile.fromTreeUri(context, uri)?.let { it.exists() && it.canWrite() } == true }
            .getOrDefault(false)
    }

    private fun root(tree: Uri): DocumentFile =
        DocumentFile.fromTreeUri(context, tree) ?: throw IOException("Ordner nicht erreichbar")

    private fun dir(tree: Uri, name: String): DocumentFile {
        val r = root(tree)
        r.findFile(name)?.takeIf { it.isDirectory }?.let { return it }
        return r.createDirectory(name) ?: throw IOException("Ordner '$name' konnte nicht angelegt werden")
    }

    /** Lesbarer Pfad, z. B. "Interner Speicher › Documents › Umgangstagebuch". */
    fun displayPath(tree: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return tree.toString()
        val parts = id.split(":", limit = 2)
        val volume = when (parts[0]) {
            "primary" -> "Interner Speicher"
            "home" -> "Dokumente"
            else -> if (tree.authority == "com.android.externalstorage.documents") "SD-Karte" else parts[0]
        }
        val path = parts.getOrNull(1).orEmpty().split("/").filter { it.isNotBlank() }
        return (listOf(volume) + path).joinToString(" › ")
    }

    suspend fun hasData(tree: Uri): Boolean = withContext(Dispatchers.IO) {
        root(tree).findFile(DATA_FILE)?.isFile == true
    }

    suspend fun load(tree: Uri): AppData? = withContext(Dispatchers.IO) {
        val file = root(tree).findFile(DATA_FILE) ?: return@withContext null
        val text = resolver.openInputStream(file.uri)?.use { it.readBytes().decodeToString() }
            ?: throw IOException("Datei konnte nicht gelesen werden")
        if (text.isBlank()) return@withContext null
        AppJson.decodeFromString(AppData.serializer(), text)
    }

    suspend fun save(tree: Uri, data: AppData) = withContext(Dispatchers.IO) {
        val bytes = AppJson.encodeToString(AppData.serializer(), data).toByteArray()
        val r = root(tree)
        val file = r.findFile(DATA_FILE) ?: r.createFile("application/json", DATA_FILE)
            ?: throw IOException("Datei konnte nicht angelegt werden")
        resolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) }
            ?: throw IOException("Datei konnte nicht geschrieben werden")
        snapshot(tree, bytes)
    }

    /** Eine Sicherung pro Tag, die ältesten werden aufgeräumt. */
    private fun snapshot(tree: Uri, bytes: ByteArray) {
        runCatching {
            val d = dir(tree, SNAPSHOT_DIR)
            val name = "sicherung-${LocalDate.now()}.json"
            val f = d.findFile(name) ?: d.createFile("application/json", name) ?: return
            resolver.openOutputStream(f.uri, "wt")?.use { it.write(bytes) }
            val all = d.listFiles().filter { it.name?.startsWith("sicherung-") == true }.sortedBy { it.name }
            if (all.size > KEEP_SNAPSHOTS) all.take(all.size - KEEP_SNAPSHOTS).forEach { it.delete() }
        }
    }

    /** Benannte Sicherung, z. B. vor einem Import. */
    suspend fun namedSnapshot(tree: Uri, name: String, data: AppData) = withContext(Dispatchers.IO) {
        val d = dir(tree, SNAPSHOT_DIR)
        val f = d.createFile("application/json", name) ?: throw IOException("Sicherung konnte nicht angelegt werden")
        resolver.openOutputStream(f.uri, "wt")?.use { it.write(AppJson.encodeToString(AppData.serializer(), data).toByteArray()) }
    }

    /** Liste aller Fotos im Ordner: Dateiname → Uri. */
    suspend fun photoIndex(tree: Uri): Map<String, Uri> = withContext(Dispatchers.IO) {
        runCatching {
            root(tree).findFile(PHOTO_DIR)?.listFiles()?.mapNotNull { f -> f.name?.let { it to f.uri } }?.toMap()
        }.getOrNull() ?: emptyMap()
    }

    /**
     * Kopiert ein Foto unverändert (Original inkl. Aufnahmedatum) in den Datenordner
     * und berechnet die Prüfsumme.
     */
    suspend fun importPhoto(tree: Uri, source: Uri, entryId: String): Pair<Photo, Uri> = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(source)?.use { it.readBytes() }
            ?: throw IOException("Foto konnte nicht gelesen werden")
        val mime = resolver.getType(source).orEmpty()
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("heic") || mime.contains("heif") -> "heic"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        val now = System.currentTimeMillis()
        val name = "foto_${entryId.take(8)}_$now.$ext"
        writePhoto(tree, name, bytes, mime.ifBlank { "image/jpeg" })?.let { uri -> Photo(name, now, sha256(bytes)) to uri }
            ?: throw IOException("Foto konnte nicht gespeichert werden")
    }

    fun writePhoto(tree: Uri, name: String, bytes: ByteArray, mime: String = "image/jpeg"): Uri? {
        val d = dir(tree, PHOTO_DIR)
        val f = d.findFile(name) ?: d.createFile(mime, name) ?: return null
        resolver.openOutputStream(f.uri, "wt")?.use { it.write(bytes) } ?: return null
        return f.uri
    }

    suspend fun deletePhoto(tree: Uri, name: String) = withContext(Dispatchers.IO) {
        runCatching { root(tree).findFile(PHOTO_DIR)?.findFile(name)?.delete() }
    }

    /** Verkleinertes, korrekt gedrehtes Bild (z. B. für den PDF-Bericht). */
    fun loadBitmap(uri: Uri, maxSize: Int): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val max = maxOf(info.size.width, info.size.height)
            if (max > maxSize) {
                val f = maxSize.toFloat() / max
                decoder.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
            }
        }
    }.getOrNull()
}
