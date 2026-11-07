package app.propcare.homeos.ui.demo

import android.content.Context
import app.propcare.homeos.data.jsonCodec
import app.propcare.homeos.domain.Triage
import kotlinx.serialization.Serializable

@Serializable data class StoredReport(
    val description: String = "", val photoPath: String? = null, val propertyId: Long? = null,
    val parentJobId: Long? = null, val triage: Triage? = null, val technicianId: String? = null,
    val sample: Boolean = false, val access: String = "", val title: String = "",
    val step: String = DemoStep.describe.name, val matchingComplete: Boolean = false,
)

/** App-private storage. Photo bytes live in the app's private photos directory. */
class ReportDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("propcare_draft", Context.MODE_PRIVATE)
    fun read(): StoredReport? = prefs.getString("report", null)?.let {
        runCatching { jsonCodec.decodeFromString(StoredReport.serializer(), it) }.getOrNull()
    }
    fun write(report: StoredReport) { prefs.edit().putString("report", jsonCodec.encodeToString(StoredReport.serializer(), report)).apply() }
    fun clear() { prefs.edit().clear().commit() }
}
