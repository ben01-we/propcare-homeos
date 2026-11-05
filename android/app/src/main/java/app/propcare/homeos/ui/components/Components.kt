package app.propcare.homeos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.propcare.homeos.data.JobEntity
import app.propcare.homeos.data.JobEventEntity
import app.propcare.homeos.data.refLabel
import app.propcare.homeos.data.statusEnum
import app.propcare.homeos.data.urgencyEnum
import app.propcare.homeos.domain.CheckState
import app.propcare.homeos.domain.JobStatus
import app.propcare.homeos.domain.Triage
import app.propcare.homeos.domain.Urgency
import app.propcare.homeos.ui.theme.PropCareTheme
import app.propcare.homeos.util.Format
import app.propcare.homeos.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** Small coloured label. Never colour alone: it always carries text. */
@Composable
fun Pill(text: String, container: Color, content: Color, icon: ImageVector? = null) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun StatusPill(status: JobStatus) {
    val c = MaterialTheme.colorScheme
    val s = PropCareTheme.status
    val (bg, fg) = when (status) {
        JobStatus.requested -> c.surfaceContainerHighest to c.onSurface
        JobStatus.quoted -> c.secondaryContainer to c.onSecondaryContainer
        JobStatus.booked, JobStatus.in_progress -> c.primaryContainer to c.onPrimaryContainer
        JobStatus.done -> s.ok to s.onOk
        JobStatus.cancelled -> c.surfaceVariant to c.onSurfaceVariant
    }
    Pill(status.label, bg, fg)
}

@Composable
fun UrgencyPill(urgency: Urgency) {
    val c = MaterialTheme.colorScheme
    val s = PropCareTheme.status
    when (urgency) {
        Urgency.emergency -> Pill(urgency.label, s.emergency, s.onEmergency, Icons.Filled.Warning)
        Urgency.urgent -> Pill(urgency.label, s.warning, s.onWarning)
        Urgency.routine -> Pill(urgency.label, c.surfaceContainerHigh, c.onSurfaceVariant)
    }
}

@Composable
fun CheckStatePill(state: CheckState) {
    val c = MaterialTheme.colorScheme
    val s = PropCareTheme.status
    when (state) {
        CheckState.ok -> Pill("OK", s.ok, s.onOk, Icons.Filled.CheckCircle)
        CheckState.due_soon -> Pill("Due soon", s.warning, s.onWarning, Icons.Filled.Info)
        CheckState.overdue -> Pill("Overdue", c.errorContainer, c.onErrorContainer, Icons.Filled.Warning)
        CheckState.missing -> Pill("No date", c.surfaceContainerHighest, c.onSurfaceVariant, Icons.Filled.RadioButtonUnchecked)
    }
}

/** A job ticket: PC-1001, title, property, status and urgency. */
@Composable
fun JobTicketCard(job: JobEntity, propertyName: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.refLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(Format.date(job.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(job.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (propertyName != null) {
                Text(propertyName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(job.statusEnum)
                if (job.statusEnum.isOpen) UrgencyPill(job.urgencyEnum)
            }
        }
    }
}

/** Home Health Score ring, 0-100. The number and band are text, so the colour is never the only signal. */
@Composable
fun HealthRing(score: Int, band: String, modifier: Modifier = Modifier, size: Dp = 112.dp) {
    val c = MaterialTheme.colorScheme
    val arc = when {
        score >= 85 -> c.primary
        score >= 65 -> c.tertiary
        score >= 40 -> c.secondary
        else -> c.error
    }
    val track = c.surfaceContainerHighest
    Box(modifier.size(size).semantics(mergeDescendants = true) { contentDescription = "Home Health Score $score out of 100, $band" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.1f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(arc, -90f, 360f * score.coerceIn(0, 100) / 100f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("of 100", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
        }
    }
}

/** Loads a stored job photo off the main thread. */
@Composable
fun rememberPhoto(path: String?, maxSide: Int = 1024): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = path?.let { p -> withContext(Dispatchers.IO) { Images.loadThumb(p, maxSide)?.asImageBitmap() } }
    }
    return bmp
}

/**
 * The photo with the AI's box drawn where the issue is. Box is [ymin, xmin, ymax, xmax] on 0-1000,
 * mapped onto a container with the photo's own aspect ratio, so the box lines up exactly.
 */
@Composable
fun PhotoWithBox(photo: ImageBitmap, box: List<Int>?, modifier: Modifier = Modifier) {
    val highlight = PropCareTheme.status.highlight
    val outline = MaterialTheme.colorScheme.inverseSurface
    Box(
        modifier.fillMaxWidth().aspectRatio(photo.width.toFloat() / photo.height.coerceAtLeast(1))
            .semantics { contentDescription = if (box != null) "Your photo, with the issue outlined" else "Your photo" },
    ) {
        Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        if (box != null) {
            Canvas(Modifier.fillMaxSize()) {
                val top = box[0] / 1000f * size.height
                val left = box[1] / 1000f * size.width
                val bottom = box[2] / 1000f * size.height
                val right = box[3] / 1000f * size.width
                val w = 3.dp.toPx()
                drawRect(outline, Offset(left, top), Size(right - left, bottom - top), style = Stroke(w * 2))
                drawRect(highlight, Offset(left, top), Size(right - left, bottom - top), style = Stroke(w))
            }
        }
    }
}

/** The AI (or offline) result, always labelled as a first look that a technician confirms. */
@Composable
fun TriageCard(t: Triage, photo: ImageBitmap?, modifier: Modifier = Modifier, note: String? = null) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        if (photo != null) PhotoWithBox(photo, t.validBox)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (t.source == "ai") Pill("AI first look, a technician confirms", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Icons.Filled.Info)
                else Pill("Offline check", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Filled.CloudOff)
            }
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!t.isPropertyIssue) {
                Text("This does not look like a property issue. You can still send it to PropCare.", style = MaterialTheme.typography.bodyMedium)
            }
            Text(t.issueSummary, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UrgencyPill(t.urgencyEnum)
                Pill(t.tradeEnum.label, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
            }
            if (t.safetyFlags.isNotEmpty()) {
                Text("Safety: " + t.safetyFlags.joinToString(", ") { it.replace('_', ' ') }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            BulletList("Likely causes", t.likelyCauses)
            BulletList("What you can do now", t.customerAdvice)
            BulletList("What a photo cannot show", t.cannotTell)
        }
    }
}

@Composable
private fun BulletList(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Requested, Quote sent, Booked, In progress, Done, with each event's note and time. */
@Composable
fun StatusTimeline(current: JobStatus, events: List<JobEventEntity>, modifier: Modifier = Modifier) {
    val steps = if (current == JobStatus.cancelled) JobStatus.flow.filter { s -> events.any { it.status == s.name } } + JobStatus.cancelled else JobStatus.flow
    val reached = if (current == JobStatus.cancelled) steps.size - 1 else JobStatus.flow.indexOf(current)
    Column(modifier) {
        steps.forEachIndexed { i, step ->
            val done = i <= reached
            val stepEvents = events.filter { it.status == step.name }
            Row(Modifier.heightIn(min = 48.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
                    Icon(
                        if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                        tint = if (step == JobStatus.cancelled) MaterialTheme.colorScheme.error else if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
                    Text(
                        step.label + if (step == current) " (now)" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    stepEvents.forEach { e ->
                        Text("${Format.dateTime(e.createdAt)}: ${e.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingState(text: String = "Loading", modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, actionLabel: String = "Try again", onRetry: (() -> Unit)? = null) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ErrorOutline, null)
            Spacer(Modifier.width(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (onRetry != null) OutlinedButton(onClick = onRetry) { Text(actionLabel) }
        }
    }
}

/** Shown when the AI check is not available, so the user knows the app still works offline. */
@Composable
fun OfflineBanner(text: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A card with padding and a surface-tone background, for grouping a section. */
@Composable
fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun ListDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
