package app.umgangstagebuch

import app.umgangstagebuch.data.AppData
import app.umgangstagebuch.data.Cancellation
import app.umgangstagebuch.data.CancelledBy
import app.umgangstagebuch.data.Channel
import app.umgangstagebuch.data.Entry
import app.umgangstagebuch.data.EntryCategory
import app.umgangstagebuch.data.Handover
import app.umgangstagebuch.data.Profile
import app.umgangstagebuch.data.Punctuality
import app.umgangstagebuch.data.Visit
import app.umgangstagebuch.data.VisitStatus
import app.umgangstagebuch.data.VisitType
import app.umgangstagebuch.data.contentHash
import app.umgangstagebuch.util.Fmt
import java.time.LocalDate
import java.time.LocalTime

/** Fiktive Beispieldaten (keine echten Personen). */
object SampleData {
    private fun at(daysFromNow: Long, h: Int, m: Int = 0) = Fmt.millis(LocalDate.now().plusDays(daysFromNow), LocalTime.of(h, m))

    val profile = Profile(
        motherName = "Anna", childName = "Lena", childBirthday = Fmt.dateToPicker(LocalDate.of(2019, 5, 14)),
        fatherName = "Max Mustermann", fatherNickname = "Stinktier", fatherEmoji = "🦨", createdAt = at(-90, 10),
    )

    val visits = listOf(
        Visit(id = "v1", start = at(-21, 15), end = at(-19, 18), status = VisitStatus.DONE, location = "Kita"),
        Visit(
            id = "v2", start = at(-14, 15), end = at(-12, 18), status = VisitStatus.CANCELLED,
            cancellation = Cancellation(CancelledBy.FATHER, "„Schaffe es nicht, muss arbeiten.“", at(-14, 11), Channel.WHATSAPP),
        ),
        Visit(id = "v3", start = at(-7, 15), end = at(-5, 18), status = VisitStatus.DONE, location = "Bahnhof"),
        Visit(id = "v4", start = at(-3, 10), end = at(-3, 17), type = VisitType.DAY, status = VisitStatus.PLANNED),
        Visit(id = "v5", start = at(4, 15), end = at(6, 18), location = "Kita"),
        Visit(id = "v6", start = at(18, 15), end = at(20, 18), location = "Kita"),
    )

    private fun e(entry: Entry) = entry.copy(createdAt = entry.eventDate + 3_600_000, updatedAt = entry.eventDate + 3_600_000).let { it.copy(hash = it.contentHash()) }

    val entries = listOf(
        e(
            Entry(
                id = "e1", eventDate = at(-19, 18, 20), category = EntryCategory.VISIT_REPORT, visitId = "v1",
                moodBefore = 4, moodAfter = 2, tagsBefore = listOf("fröhlich", "freut sich auf Umgang"),
                tags = listOf("müde / erschöpft", "weinerlich", "Bauchschmerzen"),
                pickup = Handover(Punctuality.LATE, 35, "Kam ohne Nachricht 35 Minuten zu spät."),
                dropoff = Handover(Punctuality.ON_TIME, null, "Kurze Übergabe an der Tür."),
                childQuotes = "„Papa hat gesagt, ich darf dir nichts erzählen.“",
                text = "Lena war nach der Rückkehr sehr still und wollte früh ins Bett.",
            ),
        ),
        e(Entry(id = "e2", eventDate = at(-14, 11, 5), category = EntryCategory.COMMUNICATION, title = "Kurzfristige Absage per WhatsApp", text = "Absage 4 Stunden vor Beginn. Kein Ersatztermin angeboten.")),
        e(
            Entry(
                id = "e3", eventDate = at(-5, 18, 30), category = EntryCategory.VISIT_REPORT, visitId = "v3",
                moodBefore = 3, moodAfter = 4, tags = listOf("ausgeglichen", "erzählt gern"),
                pickup = Handover(Punctuality.ON_TIME), dropoff = Handover(Punctuality.ON_TIME),
                text = "Umgang verlief ruhig. Lena hat vom Zoo erzählt.",
            ),
        ),
        e(Entry(id = "e4", eventDate = at(-2, 9, 0), category = EntryCategory.SCHOOL, title = "Rückmeldung der Kita", text = "Erzieherin berichtet, Lena sei montags oft unruhig.", moodAfter = 3)),
    )

    val data = AppData(profile = profile, visits = visits, entries = entries)
}
