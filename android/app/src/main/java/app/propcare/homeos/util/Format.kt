package app.propcare.homeos.util

import app.propcare.homeos.domain.CheckState
import app.propcare.homeos.domain.CheckStatus
import app.propcare.homeos.domain.Company
import app.propcare.homeos.domain.Trade
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared text formatting (UK money and dates), used by the screens, the job sheet and the PDF. */
object Format {
    private val uk = Locale.UK
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", uk)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", uk)

    fun pounds(pence: Int): String =
        if (pence % 100 == 0) "£" + String.format(uk, "%,d", pence / 100)
        else "£" + String.format(uk, "%,d.%02d", pence / 100, pence % 100)

    fun jobRef(ref: Int) = "PC-$ref"

    fun date(d: LocalDate): String = dateFmt.format(d)
    fun date(millis: Long): String = dateFmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    fun dateTime(millis: Long): String = dateTimeFmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /** "Typically £80 to £180", always shown with [Company.priceGuideNote]. */
    fun priceBand(trade: Trade): String? =
        Company.priceGuide[trade]?.let { (low, high) -> "Typically ${pounds(low)} to ${pounds(high)}" }

    fun checkState(s: CheckStatus): String = when (s.state) {
        CheckState.missing -> "No date recorded"
        CheckState.overdue -> "Overdue by ${-(s.daysLeft ?: 0)} days"
        CheckState.due_soon -> if (s.daysLeft == 0L) "Due today" else "Due in ${s.daysLeft} days"
        CheckState.ok -> "OK until ${s.due?.let { date(it) }}"
    }

    /** Loose UK postcode check, e.g. "M1 1AE", "SW1A 2AA". Accepts any spacing and case. */
    private val postcode = Regex("^[A-Z]{1,2}[0-9][A-Z0-9]? ?[0-9][A-Z]{2}$")
    fun isUkPostcode(s: String) = postcode.matches(s.trim().uppercase().replace(Regex("\\s+"), " "))
    fun normalisePostcode(s: String): String {
        val t = s.trim().uppercase().replace(Regex("\\s+"), "")
        return if (t.length > 3) t.dropLast(3) + " " + t.takeLast(3) else t
    }
}
