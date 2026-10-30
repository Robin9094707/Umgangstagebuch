package app.umgangstagebuch.data

import app.umgangstagebuch.util.Fmt
import java.time.LocalDate
import java.time.LocalDateTime

enum class Repeat(val label: String, val weeks: Int) {
    NONE("Einmalig", 0),
    WEEKLY("Jede Woche", 1),
    BIWEEKLY("Alle 2 Wochen", 2),
    FOURWEEKLY("Alle 4 Wochen", 4),
}

/** Erzeugt geplante Termine einer Umgangsregelung (z. B. jedes zweite Wochenende). */
fun generateSeries(
    start: LocalDateTime,
    end: LocalDateTime,
    repeat: Repeat,
    until: LocalDate,
    type: VisitType,
    location: String,
    note: String = "",
): List<Visit> {
    if (repeat == Repeat.NONE) {
        return listOf(Visit(start = Fmt.millis(start), end = Fmt.millis(end), type = type, location = location, note = note))
    }
    val series = newId()
    val out = mutableListOf<Visit>()
    var s = start
    var e = end
    while (!s.toLocalDate().isAfter(until) && out.size < 200) {
        out += Visit(start = Fmt.millis(s), end = Fmt.millis(e), type = type, location = location, note = note, seriesId = series)
        s = s.plusWeeks(repeat.weeks.toLong())
        e = e.plusWeeks(repeat.weeks.toLong())
    }
    return out
}
