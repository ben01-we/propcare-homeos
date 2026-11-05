package app.propcare.homeos.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.propcare.homeos.data.JobEntity
import app.propcare.homeos.data.ProfileEntity
import app.propcare.homeos.data.PropertyEntity
import app.propcare.homeos.data.refLabel
import app.propcare.homeos.data.tradeEnum
import app.propcare.homeos.data.triage
import app.propcare.homeos.data.urgencyEnum
import app.propcare.homeos.domain.Company
import java.io.File

/**
 * "Send to PropCare": a formatted job sheet plus the photo, by WhatsApp or email.
 * No backend: this matches how PropCare takes jobs today. Photos are shared through the
 * non-exported FileProvider with a one-off read grant.
 */
object Share {
    enum class Channel { whatsapp, email }

    fun jobSheet(job: JobEntity, property: PropertyEntity?, profile: ProfileEntity?): String = buildString {
        appendLine("PropCare job request ${job.refLabel}")
        appendLine()
        appendLine("Issue: ${job.title}")
        appendLine("Urgency: ${job.urgencyEnum.label}")
        appendLine("Trade: ${job.tradeEnum.label}")
        if (property != null) {
            appendLine("Property: ${property.name}")
            appendLine("Address: ${listOf(property.addressLine, property.town, property.postcode).filter { it.isNotBlank() }.joinToString(", ")}")
        }
        if (job.description.isNotBlank()) { appendLine(); appendLine("Description: ${job.description}") }
        job.triage()?.let { t ->
            appendLine()
            appendLine(if (t.source == "ai") "AI first look (a technician confirms):" else "Offline check (a technician confirms):")
            appendLine("- ${t.issueSummary}")
            if (t.likelyCauses.isNotEmpty()) appendLine("- Likely causes: ${t.likelyCauses.joinToString("; ")}")
            if (t.safetyFlags.isNotEmpty()) appendLine("- Safety flags: ${t.safetyFlags.joinToString(", ") { it.replace('_', ' ') }}")
        }
        if (job.accessNotes.isNotBlank()) appendLine("Access: ${job.accessNotes}")
        if (job.preferredTimes.isNotBlank()) appendLine("Preferred times: ${job.preferredTimes}")
        if (profile != null) {
            appendLine()
            appendLine("Customer: ${profile.fullName}, ${profile.phone}, ${profile.postcode}")
        }
        appendLine()
        append("Sent from the PropCare app (concept).")
    }

    fun photoUri(context: Context, path: String?): Uri? {
        val f = path?.let { File(it) }?.takeIf { it.exists() } ?: return null
        return runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", f) }.getOrNull()
    }

    /** Opens WhatsApp (or email) with the job sheet. Returns false if no app could take it. */
    fun send(context: Context, channel: Channel, subject: String, text: String, photo: Uri?): Boolean {
        val base = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            if (photo != null) {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, photo)
                clipData = ClipData.newRawUri("", photo)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        return when (channel) {
            Channel.whatsapp -> {
                val direct = Intent(base).setPackage("com.whatsapp").putExtra("jid", "${Company.whatsapp}@s.whatsapp.net")
                start(context, direct) || start(context, Intent.createChooser(base, "Send to PropCare"))
            }
            Channel.email -> {
                val mail = Intent(base).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(Company.email))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                }
                start(context, mail) || start(context, Intent.createChooser(Intent(base).putExtra(Intent.EXTRA_SUBJECT, subject), "Email PropCare"))
            }
        }
    }

    /** Shares any file we created (e.g. the passport PDF) with the system share sheet. */
    fun shareFile(context: Context, uri: Uri, mime: String, title: String): Boolean {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(context, Intent.createChooser(i, title))
    }

    /** Dials a number (no CALL_PHONE permission needed). */
    fun dial(context: Context, number: String): Boolean =
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' })))

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent); true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
