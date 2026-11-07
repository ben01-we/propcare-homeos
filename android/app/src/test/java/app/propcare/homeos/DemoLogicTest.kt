package app.propcare.homeos

import app.propcare.homeos.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DemoLogicTest {
    private val zone = ZoneId.of("Europe/London")
    private val start = LocalDate.of(2026, 11, 6)
    private val instant = start.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    @Test fun warrantyUsesCalendarDaysAndExclusiveExpiry() {
        val coverage = RepairCoverage(instant, 90)
        assertEquals(LocalDate.of(2027, 2, 4), coverage.expires(zone))
        assertEquals(90L, coverage.remaining(start, zone))
        assertEquals(1L, coverage.remaining(LocalDate.of(2027, 2, 3), zone))
        assertFalse(coverage.active(LocalDate.of(2027, 2, 4), zone))
        assertEquals(0L, coverage.remaining(LocalDate.of(2027, 5, 1), zone))
        assertFalse(coverage.active(start.minusDays(1), zone))
    }
    @Test fun daylightSavingDoesNotChangeDayCount() {
        val spring = LocalDate.of(2026, 3, 28).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val coverage = RepairCoverage(spring, 3)
        assertEquals(LocalDate.of(2026, 3, 31), coverage.expires(zone))
        assertEquals(2L, coverage.remaining(LocalDate.of(2026, 3, 29), zone))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsZeroDuration() { RepairCoverage(instant, 0) }
    @Test fun matchingDoesNotAssignWrongTrade() {
        assertTrue(DemoDirectory.matching(Trade.plumber).all { it.trade == Trade.plumber })
        assertTrue(DemoDirectory.matching(Trade.plasterer).all { it.trade == Trade.plasterer })
        assertEquals("jamie", DemoDirectory.matching(Trade.plumber).first().id)
    }
    @Test fun sampleCannotSuppressSafetyWarning() {
        val sample = Triage(issueSummary = "Tap", source = "demo", urgency = "routine", trade = "plumber")
        assertEquals(Urgency.emergency, Safety.enforce(sample, "I smell gas").urgencyEnum)
        assertEquals(Urgency.emergency, Safety.enforce(sample, "Sparking socket").urgencyEnum)
    }
}
