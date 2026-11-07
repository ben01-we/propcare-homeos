@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.propcare.homeos.ui.demo

import android.animation.ValueAnimator
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.propcare.homeos.R
import app.propcare.homeos.data.JobEntity
import app.propcare.homeos.domain.*
import app.propcare.homeos.ui.theme.PropCareTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

@Composable fun motionAllowed(): Boolean {
    val context = LocalContext.current
    val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    return ValueAnimator.areAnimatorsEnabled() && accessibility?.isTouchExplorationEnabled != true
}

@Composable fun AnalysisProcessing(animate: Boolean = motionAllowed()) {
    val transition = rememberInfiniteTransition(label = "Analysis glow")
    val pulse by transition.animateFloat(.72f, 1f,
        infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "Blue glow")
    val strength = if (animate) pulse else .85f
    val blue = PropCareTheme.status.highlight
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.radialGradient(listOf(blue.copy(alpha = .42f * strength), blue.copy(alpha = .10f), blue.copy(alpha = 0f))), radius = size.minDimension / 2)
            }
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
                Image(painterResource(R.drawable.brand_logo), "PropCare is analysing your photo", Modifier.size(112.dp).padding(14.dp))
            }
        }
        Text("Taking a closer look.", style = MaterialTheme.typography.headlineMedium)
        Text("Checking the photo and your description", style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(Modifier.width(190.dp))
        DemoNote("Looking for useful clues, then the right trade.")
    }
}

@Composable fun TechnicianMetrics(technician: DemoTechnician) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(5.dp)); Text(technician.sampleRating, style = MaterialTheme.typography.titleLarge)
            }
            DemoNote("Sample rating")
        }
        Column(Modifier.weight(1f)) {
            Text("${technician.sampleHomes}", style = MaterialTheme.typography.titleLarge)
            DemoNote("Homes · sample count")
        }
    }
}

@Composable fun MatchingCarousel(trade: Trade, technicianId: String?, complete: Boolean,
    onChoose: (String) -> Unit, onComplete: () -> Unit) {
    val candidates = DemoDirectory.matching(trade)
    val selected = candidates.firstOrNull { it.id == technicianId } ?: candidates.firstOrNull()
    val latestComplete by rememberUpdatedState(onComplete)
    val allowed = motionAllowed()
    if (selected == null) {
        LaunchedEffect(trade) { latestComplete() }
        DemoNote("No matching profile yet. Send the repair brief to PropCare.")
        return
    }
    val reel = remember(selected.id) { DemoDirectory.technicians.filter { it.id != selected.id }.take(4) + selected }
    val pager = rememberPagerState(initialPage = if (complete) reel.lastIndex else 0, pageCount = { reel.size })
    LaunchedEffect(selected.id, complete, allowed) {
        if (complete || !allowed) {
            pager.scrollToPage(reel.lastIndex)
            if (!complete) latestComplete()
        } else {
            delay(250)
            for (page in 1..reel.lastIndex) pager.animateScrollToPage(page, animationSpec = tween(220 + page * 110, easing = FastOutSlowInEasing))
            latestComplete()
        }
    }
    Text(if (complete) "A match for your repair." else "Finding the right technician…", style = MaterialTheme.typography.headlineMedium)
    DemoNote(if (complete) "${trade.label} · suggested from the sample directory" else "Comparing trades and repair specialties")
    HorizontalPager(state = pager, userScrollEnabled = false, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp), pageSpacing = 16.dp) { page ->
        val technician = reel[page]
        val scale by animateFloatAsState(if (complete && page == reel.lastIndex) 1f else .92f, tween(if (allowed) 500 else 0), label = "Selected technician zoom")
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (technician.id == "jamie") Image(painterResource(R.drawable.demo_technician), "Illustrative technician photo",
                    Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                else Box(Modifier.fillMaxWidth().height(140.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Text(technician.initials, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(technician.name, style = MaterialTheme.typography.titleLarge)
                Text(technician.trade.label, style = MaterialTheme.typography.bodyLarge)
                TechnicianMetrics(technician)
                Text(technician.specialties, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (!complete) TextButton(onClick = onComplete, modifier = Modifier.heightIn(min = 48.dp)) { Text("Skip animation") }
    else if (candidates.size > 1) {
        Text("Compare another match", style = MaterialTheme.typography.titleSmall)
        candidates.forEach { person ->
            FilterChip(selected = person.id == selected.id, onClick = { onChoose(person.id) },
                label = { Text(person.name) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    DemoNote("Sample profiles, ratings and home counts. PropCare confirms the actual technician and availability.")
}

@Composable fun WarrantyStamp(jobId: Long) {
    val allowed = motionAllowed()
    var landed by rememberSaveable(jobId) { mutableStateOf(!allowed) }
    LaunchedEffect(jobId) { landed = true }
    val progress by animateFloatAsState(if (landed) 1f else 0f,
        animationSpec = tween(if (allowed) 550 else 0, easing = FastOutSlowInEasing), label = "Warranty issued stamp")
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.graphicsLayer { scaleX = 1.4f - .4f * progress; scaleY = scaleX; rotationZ = -16f + 12f * progress; alpha = .4f + .6f * progress }) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.VerifiedUser, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                Text("WARRANTY ISSUED", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable fun WarrantyCountdown(job: JobEntity) {
    val coverage = job.coverage() ?: return
    var now by remember(job.id) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(job.id) { while (true) { now = System.currentTimeMillis(); delay(60_000) } }
    val end = coverage.expires().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val minutes = ((end - now).coerceAtLeast(0) / 60_000)
    val text = if (minutes == 0L) "Warranty ended" else "${minutes / 1440}d ${(minutes % 1440) / 60}h ${minutes % 60}m left"
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Timer, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
