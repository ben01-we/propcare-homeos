package app.propcare.homeos

import app.propcare.homeos.domain.CheckState
import app.propcare.homeos.domain.Compliance
import app.propcare.homeos.domain.HealthScore
import app.propcare.homeos.domain.PropertyType
import app.propcare.homeos.domain.Safety
import app.propcare.homeos.domain.Triage
import app.propcare.homeos.domain.Urgency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogicTest {
    private val routine = Triage(issueSummary = "Stain", trade = "plumber", urgency = "routine", source = "ai")

    @Test fun gasSmellIsAlwaysAnEmergencyForAGasEngineer() {
        val t = Safety.enforce(routine, "There is a smell of gas in the kitchen")
        assertEquals("emergency", t.urgency)
        assertEquals("gas_engineer", t.trade)
    }

    @Test fun waterNearElectricsGoesToAnElectrician() {
        val t = Safety.enforce(routine, "water dripping onto the light switch in the hall")
        assertEquals("emergency", t.urgency)
        assertEquals("electrician", t.trade)
    }

    @Test fun sparkingWithoutAPhotoIsStillAnEmergency() {
        assertEquals("emergency", Safety.textOnly("Sparking from the socket and a burning smell").urgency)
    }

    @Test fun ordinaryReportsAreNotEscalated() {
        assertTrue(Safety.flagsFromText("paint peeling in the bedroom").isEmpty())
        assertEquals("routine", Safety.enforce(routine, "small drip under the sink").urgency)
        assertEquals("urgent", Safety.textOnly("gas boiler not heating the water").urgency)
    }

    @Test fun burstPipeIsAtLeastUrgent() {
        assertEquals("urgent", Safety.enforce(routine, "burst pipe in the loft").urgency)
    }

    @Test fun checkStates() {
        val today = LocalDate.of(2026, 11, 6)
        val gas = Compliance.rule("gas_safety")
        assertEquals(CheckState.overdue, Compliance.status(gas, "2025-10-01", today).state)
        assertEquals(CheckState.due_soon, Compliance.status(gas, "2025-11-20", today).state)
        assertEquals(CheckState.ok, Compliance.status(Compliance.rule("eicr"), "2024-01-01", today).state)
        assertEquals(CheckState.missing, Compliance.status(Compliance.rule("epc"), null, today).state)
        assertEquals(LocalDate.of(2025, 2, 28), Compliance.status(gas, "2024-02-29", today).due)
    }

    @Test fun healthScoreLosesPointsAndExplainsHowToWinThemBack() {
        val today = LocalDate.of(2026, 11, 6)
        val checks = Compliance.rulesFor(PropertyType.rental).map { Compliance.status(it, if (it.kind == "gas_safety") "2025-10-01" else "2026-06-01", today) }
        val h = HealthScore.compute(PropertyType.rental, checks, listOf(HealthScore.OpenIssue(1, "Leak", Urgency.urgent)))
        assertEquals(70, h.score)
        assertEquals("gas_safety", h.tips.first().checkKind)
        assertEquals(100, HealthScore.compute(PropertyType.home, emptyList(), emptyList()).score)
    }
}
