package app.umgangstagebuch.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Fmt {
    private val de = Locale.GERMANY
    val zone: ZoneId get() = ZoneId.systemDefault()

    private val date = DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", de)
    private val dateLong = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", de)
    private val dateShort = DateTimeFormatter.ofPattern("dd.MM.yyyy", de)
    private val time = DateTimeFormatter.ofPattern("HH:mm", de)
    private val dateTime = DateTimeFormatter.ofPattern("EEE, d. MMM yyyy, HH:mm", de)
    private val stamp = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss 'Uhr' (z)", de)
    private val month = DateTimeFormatter.ofPattern("MMMM yyyy", de)
    private val weekdayShort = DateTimeFormatter.ofPattern("EE", de)
    private val monthShort = DateTimeFormatter.ofPattern("MMM", de)
    private val fileStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm", de)

    fun ldt(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    fun ld(millis: Long): LocalDate = ldt(millis).toLocalDate()
    fun millis(d: LocalDate, t: LocalTime = LocalTime.MIDNIGHT): Long = d.atTime(t).atZone(zone).toInstant().toEpochMilli()
    fun millis(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()

    /** Material DatePicker arbeitet mit UTC-Mitternacht. */
    fun pickerToDate(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    fun dateToPicker(d: LocalDate): Long = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun date(m: Long): String = date.format(ldt(m))
    fun dateLong(m: Long): String = dateLong.format(ldt(m))
    fun dateShort(m: Long): String = dateShort.format(ldt(m))
    fun dateShort(d: LocalDate): String = dateShort.format(d)
    fun date(d: LocalDate): String = date.format(d)
    fun time(m: Long): String = time.format(ldt(m))
    fun time(t: LocalTime): String = time.format(t)
    fun dateTime(m: Long): String = dateTime.format(ldt(m))
    fun stamp(m: Long): String = stamp.format(Instant.ofEpochMilli(m).atZone(zone))
    fun month(m: Long): String = month.format(ldt(m)).replaceFirstChar { it.uppercase() }
    fun weekdayShort(m: Long): String = weekdayShort.format(ldt(m)).removeSuffix(".")
    fun monthShort(m: Long): String = monthShort.format(ldt(m)).removeSuffix(".")
    fun day(m: Long): String = ldt(m).dayOfMonth.toString()
    fun fileStamp(m: Long = System.currentTimeMillis()): String = fileStamp.format(ldt(m))

    fun range(start: Long, end: Long): String {
        val s = ldt(start); val e = ldt(end)
        return if (s.toLocalDate() == e.toLocalDate()) "${date(start)}, ${time(start)} – ${time(end)} Uhr"
        else "${dateTime(start)} – ${dateTime(end)} Uhr"
    }

    /** "heute", "morgen", "in 3 Tagen", "vor 2 Tagen" … */
    fun relativeDay(m: Long, now: Long = System.currentTimeMillis()): String {
        val days = ChronoUnit.DAYS.between(ld(now), ld(m))
        return when {
            days == 0L -> "heute"
            days == 1L -> "morgen"
            days == 2L -> "übermorgen"
            days == -1L -> "gestern"
            days > 0 && days < 14 -> "in $days Tagen"
            days < 0 && days > -14 -> "vor ${-days} Tagen"
            days > 0 -> "in ${days / 7} Wochen"
            else -> "vor ${-days / 7} Wochen"
        }
    }

    fun duration(start: Long, end: Long): String {
        val minutes = (end - start) / 60_000
        val d = minutes / (60 * 24); val h = (minutes / 60) % 24; val min = minutes % 60
        return buildList {
            if (d > 0) add(if (d == 1L) "1 Tag" else "$d Tage")
            if (h > 0) add("$h Std.")
            if (min > 0 && d == 0L) add("$min Min.")
        }.joinToString(" ").ifBlank { "0 Min." }
    }

    fun greeting(): String = when (LocalTime.now().hour) {
        in 5..10 -> "Guten Morgen"
        in 11..17 -> "Hallo"
        in 18..22 -> "Guten Abend"
        else -> "Gute Nacht"
    }

    fun age(birthday: Long?): String? {
        birthday ?: return null
        val b = pickerToDate(birthday)
        val now = LocalDate.now()
        val years = ChronoUnit.YEARS.between(b, now)
        return if (years >= 2) "$years Jahre" else "${ChronoUnit.MONTHS.between(b, now)} Monate"
    }
}
