package app.propcare.homeos.domain

data class ScoreTip(val text: String, val points: Int, val checkKind: String? = null, val jobId: Long? = null)

data class Health(val score: Int, val tips: List<ScoreTip>) {
    val band: String get() = when {
        score >= 85 -> "Looking after itself"
        score >= 65 -> "A few things to sort"
        score >= 40 -> "Needs attention"
        else -> "Act now"
    }
}

/**
 * Home Health Score, 0-100: starts at 100 and loses points for overdue or missing safety
 * checks and for open issues. Each lost point comes with a tip that wins it back.
 * Deterministic, explainable, and costs no AI calls.
 */
object HealthScore {
    data class OpenIssue(val id: Long, val title: String, val urgency: Urgency)

    fun compute(type: PropertyType, checks: List<CheckStatus>, openIssues: List<OpenIssue>): Health {
        val tips = mutableListOf<ScoreTip>()
        val rental = type == PropertyType.rental
        for (c in checks) {
            val (lost, text) = when (c.state) {
                CheckState.overdue -> (if (rental) 20 else 10) to "Book the overdue ${c.rule.name.lowercase()}"
                CheckState.missing -> (if (rental) 10 else 5) to "Add the date of the last ${c.rule.name.lowercase()}"
                CheckState.due_soon -> 5 to "Book the ${c.rule.name.lowercase()} due in ${c.daysLeft} days"
                CheckState.ok -> 0 to ""
            }
            if (lost > 0) tips += ScoreTip(text, lost, checkKind = c.rule.kind)
        }
        for (i in openIssues) {
            val lost = when (i.urgency) { Urgency.emergency -> 25; Urgency.urgent -> 10; Urgency.routine -> 5 }
            tips += ScoreTip("Get \"${i.title}\" fixed", lost, jobId = i.id)
        }
        val score = (100 - tips.sumOf { it.points }).coerceIn(0, 100)
        return Health(score, tips.sortedByDescending { it.points })
    }
}
