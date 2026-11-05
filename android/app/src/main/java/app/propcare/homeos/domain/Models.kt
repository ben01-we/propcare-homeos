package app.propcare.homeos.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Trade(val label: String) {
    plumber("Plumber"), electrician("Electrician"), gas_engineer("Gas Safe engineer"), heating("Heating engineer"),
    roofer("Roofer"), carpenter("Carpenter"), decorator("Decorator"), plasterer("Plasterer"),
    flooring("Flooring fitter"), locksmith("Locksmith"), damp("Damp specialist"), general("General maintenance");

    companion object { fun of(s: String?) = entries.firstOrNull { it.name == s } ?: general }
}

enum class Urgency(val label: String) {
    emergency("Emergency"), urgent("Within a few days"), routine("Routine");

    companion object { fun of(s: String?) = entries.firstOrNull { it.name == s } ?: routine }
}

enum class JobStatus(val label: String) {
    requested("Requested"), quoted("Quote sent"), booked("Booked"), in_progress("In progress"), done("Done"), cancelled("Cancelled");

    val isOpen get() = this != done && this != cancelled

    companion object {
        val flow = listOf(requested, quoted, booked, in_progress, done)
        fun of(s: String?) = entries.firstOrNull { it.name == s } ?: requested
    }
}

enum class PropertyType(val label: String) {
    home("My home"), rental("Rental I let out"), commercial("Commercial");

    companion object { fun of(s: String?) = entries.firstOrNull { it.name == s } ?: home }
}

/** Result of a photo check. `box` is [ymin, xmin, ymax, xmax] on a 0-1000 scale (Gemini convention). */
@Serializable
data class Triage(
    val isPropertyIssue: Boolean = true,
    val issueSummary: String,
    val likelyCauses: List<String> = emptyList(),
    val trade: String = "general",
    val urgency: String = "routine",
    val safetyFlags: List<String> = emptyList(),
    val cannotTell: List<String> = emptyList(),
    val customerAdvice: List<String> = emptyList(),
    val confidence: String = "low",
    val source: String = "offline",
    val model: String? = null,
    val box: List<Int>? = null,
    val labels: List<String> = emptyList(),
) {
    val tradeEnum get() = Trade.of(trade)
    val urgencyEnum get() = Urgency.of(urgency)
    val validBox: List<Int>?
        get() = box?.takeIf { b -> b.size == 4 && b.all { it in 0..1000 } && b[2] > b[0] && b[3] > b[1] }
}

@Serializable
data class TriageResponse(val triage: Triage? = null, @SerialName("error") val error: String? = null)
