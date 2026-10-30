package app.umgangstagebuch

import app.umgangstagebuch.data.AppData
import app.umgangstagebuch.data.AppJson
import app.umgangstagebuch.data.Repeat
import app.umgangstagebuch.data.VisitType
import app.umgangstagebuch.data.contentHash
import app.umgangstagebuch.data.generateSeries
import app.umgangstagebuch.data.isIntact
import app.umgangstagebuch.data.stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class LogicTest {

    @Test
    fun hashDetectsTampering() {
        val e = SampleData.entries.first()
        assertTrue(e.isIntact())
        assertFalse(e.copy(text = "manipuliert").isIntact())
        assertEquals(e.contentHash(), e.copy(revisions = emptyList()).contentHash())
    }

    @Test
    fun statsCountFatherCancellations() {
        val s = SampleData.visits.stats(now = System.currentTimeMillis())
        assertEquals(2, s.done)
        assertEquals(1, s.cancelledByFather)
        assertEquals(1, s.shortNotice)
        assertEquals(66, s.reliability)
    }

    @Test
    fun biweeklySeries() {
        val start = LocalDateTime.of(2026, 1, 2, 15, 0)
        val list = generateSeries(start, start.plusDays(2).withHour(18), Repeat.BIWEEKLY, LocalDate.of(2026, 3, 1), VisitType.WEEKEND, "Kita")
        assertEquals(5, list.size)
        assertTrue(list.all { it.seriesId == list.first().seriesId })
    }

    @Test
    fun jsonRoundTrip() {
        val json = AppJson.encodeToString(AppData.serializer(), SampleData.data)
        val back = AppJson.decodeFromString(AppData.serializer(), json)
        assertEquals(SampleData.data.copy(savedAt = 0), back.copy(savedAt = 0))
        assertTrue(back.entries.all { it.isIntact() })
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val json = """{"formatVersion":2,"neuesFeld":true,"profile":{"motherName":"A","childName":"B"}}"""
        val d = AppJson.decodeFromString(AppData.serializer(), json)
        assertEquals("B", d.profile.childName)
    }
}
