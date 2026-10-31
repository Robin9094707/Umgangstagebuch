package app.umgangstagebuch.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
data class AppData(
    val formatVersion: Int = 1,
    val profile: Profile = Profile(),
    val visits: List<Visit> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val recordings: List<Recording> = emptyList(),
    val savedAt: Long = 0L,
)

@Serializable
data class GeoPoint(
    val lat: Double,
    val lng: Double,
    val accuracy: Float? = null,
    val capturedAt: Long = 0L,
) {
    /** Kurze, lesbare Koordinatenangabe. */
    fun pretty(): String {
        fun fmt(v: Double, pos: String, neg: String) = "%.5f° %s".format(kotlin.math.abs(v), if (v >= 0) pos else neg)
        return "${fmt(lat, "N", "S")}, ${fmt(lng, "O", "W")}"
    }
}

@Serializable
data class RecordingRename(val at: Long, val from: String, val to: String)

@Serializable
data class Recording(
    val id: String = newId(),
    /** Dateiname im Ordner „aufnahmen". */
    val fileName: String,
    /** Fest: Beginn der Aufnahme. */
    val startedAt: Long,
    /** Fest: Zeitpunkt, zu dem die Aufnahme gespeichert wurde. */
    val createdAt: Long,
    val durationMs: Long,
    /** Einziges änderbares Feld. */
    val title: String = "",
    /** Fest: Ort, an dem aufgenommen wurde. */
    val location: GeoPoint? = null,
    /** Lautstärkeverlauf (0..100) zum Anzeigen der Wellenform. */
    val waveform: List<Int> = emptyList(),
    val sha256: String = "",
    val mimeType: String = "audio/mp4",
    val renames: List<RecordingRename> = emptyList(),
) {
    fun displayTitle(index: Int = 0): String =
        title.ifBlank { "Aufnahme" + (if (index > 0) " $index" else "") }
}

@Serializable
data class Profile(
    val motherName: String = "",
    val childName: String = "",
    val childBirthday: Long? = null,
    val childDetails: String = "",
    val fatherName: String = "",
    val fatherNickname: String = "",
    val fatherEmoji: String = "",
    val useNickname: Boolean = true,
    val defaultLocation: String = "",
    val createdAt: Long = 0L,
) {
    val isComplete: Boolean get() = motherName.isNotBlank() && childName.isNotBlank()

    /** Name für die App-Oberfläche – gern mit Spitznamen. */
    val fatherDisplay: String
        get() {
            val base = if (useNickname && fatherNickname.isNotBlank()) fatherNickname.trim()
            else fatherName.trim().ifBlank { "Vater" }
            return if (fatherEmoji.isNotBlank()) "$base ${fatherEmoji.trim()}" else base
        }

    /** Name für offizielle Berichte – immer der echte Name. */
    val fatherOfficial: String get() = fatherName.trim().ifBlank { "Kindsvater" }

    val childDisplay: String get() = childName.trim().ifBlank { "Kind" }
}

@Serializable
enum class VisitType(val label: String) {
    WEEKEND("Wochenende"),
    DAY("Tagesumgang"),
    OVERNIGHT("Mit Übernachtung"),
    HOLIDAY("Ferien / Feiertag"),
    ACCOMPANIED("Begleiteter Umgang"),
    PHONE("Telefonat"),
    VIDEO("Videoanruf"),
    OTHER("Sonstiges"),
}

@Serializable
enum class VisitStatus(val label: String) {
    PLANNED("Geplant"),
    DONE("Hat stattgefunden"),
    SHORTENED("Verkürzt / vorzeitig beendet"),
    CANCELLED("Abgesagt"),
    NO_SHOW("Nicht erschienen"),
}

@Serializable
enum class CancelledBy(val label: String) {
    FATHER("Vater"),
    MOTHER("Ich (Mutter)"),
    CHILD_ILL("Kind krank"),
    MUTUAL("Einvernehmlich"),
    OTHER("Sonstiges"),
}

@Serializable
enum class Channel(val label: String) {
    WHATSAPP("WhatsApp / Messenger"),
    SMS("SMS"),
    CALL("Anruf"),
    EMAIL("E-Mail"),
    IN_PERSON("Persönlich"),
    LAWYER("Über Anwalt / Jugendamt"),
    NONE("Gar nicht"),
}

@Serializable
data class Cancellation(
    val by: CancelledBy = CancelledBy.FATHER,
    val reason: String = "",
    val notifiedAt: Long? = null,
    val channel: Channel = Channel.WHATSAPP,
    val replacementOffered: Boolean = false,
    val recordedAt: Long = 0L,
) {
    /** Stunden zwischen Absage und geplantem Beginn. */
    fun noticeHours(visitStart: Long): Long? = notifiedAt?.let { (visitStart - it) / 3_600_000L }
}

@Serializable
data class Visit(
    val id: String = newId(),
    val start: Long,
    val end: Long,
    val type: VisitType = VisitType.WEEKEND,
    val location: String = "",
    val note: String = "",
    val status: VisitStatus = VisitStatus.PLANNED,
    val cancellation: Cancellation? = null,
    val seriesId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class EntryCategory(val label: String, val short: String) {
    VISIT_REPORT("Umgangsbericht", "Umgang"),
    HANDOVER("Übergabe", "Übergabe"),
    CHILD("Verhalten & Befinden", "Befinden"),
    COMMUNICATION("Kommunikation mit dem Vater", "Kommunikation"),
    HEALTH("Gesundheit & Arzt", "Gesundheit"),
    SCHOOL("Kita / Schule", "Kita/Schule"),
    OFFICIAL("Jugendamt / Gericht / Anwalt", "Behörden"),
    OTHER("Sonstiges", "Sonstiges"),
}

@Serializable
enum class Punctuality(val label: String) {
    ON_TIME("Pünktlich"),
    LATE("Verspätet"),
    EARLY("Zu früh"),
    MISSED("Nicht erschienen"),
}

@Serializable
data class Handover(
    val punctuality: Punctuality? = null,
    val delayMinutes: Int? = null,
    val note: String = "",
)

@Serializable
data class Photo(
    val fileName: String,
    val addedAt: Long,
    val sha256: String = "",
    val caption: String = "",
)

@Serializable
data class Revision(
    val editedAt: Long,
    val previousHash: String,
    val previousJson: String,
)

@Serializable
data class Entry(
    val id: String = newId(),
    /** Automatisch beim ersten Speichern gesetzt – nicht änderbar. */
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** Wann das Ereignis stattfand (vom Nutzer wählbar). */
    val eventDate: Long = System.currentTimeMillis(),
    val category: EntryCategory = EntryCategory.OTHER,
    val visitId: String? = null,
    val title: String = "",
    val text: String = "",
    val moodBefore: Int? = null,
    val moodAfter: Int? = null,
    val tagsBefore: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val pickup: Handover? = null,
    val dropoff: Handover? = null,
    val childQuotes: String = "",
    val health: String = "",
    val witnesses: String = "",
    val photos: List<Photo> = emptyList(),
    val revisions: List<Revision> = emptyList(),
    val hash: String = "",
) {
    val isEdited: Boolean get() = revisions.isNotEmpty()
}

object Tags {
    val groups: List<Pair<String, List<String>>> = listOf(
        "Stimmung" to listOf(
            "fröhlich", "ausgeglichen", "entspannt", "aufgedreht", "traurig", "weinerlich",
            "ängstlich", "wütend", "gereizt", "verunsichert", "still / zurückgezogen", "anhänglich",
        ),
        "Verhalten" to listOf(
            "erzählt gern", "will nicht darüber reden", "wollte nicht hin", "freut sich auf Umgang",
            "aggressiv", "trotzig", "Rückfall ins Babyverhalten", "Konzentrationsprobleme",
        ),
        "Körper & Schlaf" to listOf(
            "müde / erschöpft", "Schlafprobleme", "Albträume", "Bauchschmerzen", "Kopfschmerzen",
            "Essprobleme", "Einnässen", "krank", "Verletzung / blaue Flecken",
        ),
        "Versorgung" to listOf(
            "hungrig", "ungewaschen", "unpassend gekleidet", "Sachen fehlen", "Medikamente nicht gegeben",
            "Hausaufgaben nicht gemacht", "zu spät ins Bett",
        ),
    )
    val concerning: Set<String> = setOf(
        "traurig", "weinerlich", "ängstlich", "wütend", "gereizt", "verunsichert", "still / zurückgezogen",
        "will nicht darüber reden", "wollte nicht hin", "aggressiv", "Rückfall ins Babyverhalten",
        "Schlafprobleme", "Albträume", "Bauchschmerzen", "Kopfschmerzen", "Essprobleme", "Einnässen",
        "Verletzung / blaue Flecken", "hungrig", "ungewaschen", "unpassend gekleidet", "Sachen fehlen",
        "Medikamente nicht gegeben", "Hausaufgaben nicht gemacht", "zu spät ins Bett",
    )
}

object Moods {
    val emoji = listOf("😢", "😟", "😐", "🙂", "😄")
    val label = listOf("Sehr schlecht", "Eher schlecht", "Neutral", "Gut", "Sehr gut")
    fun emojiOf(v: Int?) = v?.let { emoji.getOrNull(it - 1) } ?: "–"
    fun labelOf(v: Int?) = v?.let { label.getOrNull(it - 1) } ?: "keine Angabe"
}
