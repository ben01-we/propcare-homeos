package app.propcare.homeos.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class CheckRule(val kind: String, val name: String, val rule: String, val months: Long, val trade: Trade)

enum class CheckState { missing, overdue, due_soon, ok }

data class CheckStatus(val rule: CheckRule, val lastDone: LocalDate?, val due: LocalDate?, val daysLeft: Long?, val state: CheckState)

/** Landlord safety checks, England rules for privately rented homes. */
object Compliance {
    val rules = listOf(
        CheckRule("gas_safety", "Gas safety check", "Every gas appliance and flue checked by a Gas Safe engineer every year.", 12, Trade.gas_engineer),
        CheckRule("eicr", "Electrical safety report (EICR)", "Electrical installation inspected and tested at least every 5 years.", 60, Trade.electrician),
        CheckRule("epc", "Energy Performance Certificate", "An EPC is valid for 10 years.", 120, Trade.general),
        CheckRule("alarms", "Smoke and CO alarms", "A smoke alarm on every storey and a CO alarm by fuel-burning appliances, checked at the start of each tenancy.", 12, Trade.electrician),
    )

    /** Homes that are not let still benefit from a yearly boiler/gas check and working alarms. */
    fun rulesFor(type: PropertyType) = if (type == PropertyType.rental) rules else rules.filter { it.kind == "gas_safety" || it.kind == "alarms" }

    fun rule(kind: String) = rules.first { it.kind == kind }

    fun status(rule: CheckRule, lastDoneIso: String?, today: LocalDate = LocalDate.now()): CheckStatus {
        val last = lastDoneIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return CheckStatus(rule, null, null, null, CheckState.missing)
        val due = last.plusMonths(rule.months)
        val days = ChronoUnit.DAYS.between(today, due)
        val state = when {
            days < 0 -> CheckState.overdue
            days <= 30 -> CheckState.due_soon
            else -> CheckState.ok
        }
        return CheckStatus(rule, last, due, days, state)
    }
}
