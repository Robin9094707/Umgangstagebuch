package app.umgangstagebuch.data

import kotlinx.serialization.json.Json
import java.io.InputStream
import java.security.MessageDigest

val AppJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

private val CanonicalJson = Json { encodeDefaults = true; prettyPrint = false }

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

fun sha256(input: InputStream): String {
    val md = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        md.update(buf, 0, n)
    }
    return md.digest().toHex()
}

/** Prüfsumme über den Inhalt eines Eintrags (ohne Hash und Versionsverlauf). */
fun Entry.contentHash(): String =
    sha256(CanonicalJson.encodeToString(Entry.serializer(), copy(hash = "", revisions = emptyList())).toByteArray())

fun Entry.isIntact(): Boolean = hash.isEmpty() || hash == contentHash()

fun Entry.toCanonicalJson(): String =
    CanonicalJson.encodeToString(Entry.serializer(), copy(revisions = emptyList()))

fun entryFromJson(json: String): Entry? = runCatching { AppJson.decodeFromString(Entry.serializer(), json) }.getOrNull()
