package app.propcare.homeos.ui.demo

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import app.propcare.homeos.AppViewModel
import app.propcare.homeos.data.*
import app.propcare.homeos.domain.*
import app.propcare.homeos.ui.screens.*
import app.propcare.homeos.util.Images
import app.propcare.homeos.util.Share
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable fun DemoApp(vm: AppViewModel, snackbar: SnackbarHostState) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val properties by vm.properties.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val openProperty by vm.openProperty.collectAsStateWithLifecycle()
    val roleName by vm.selectedRole.collectAsStateWithLifecycle()
    val role = DemoRole.valueOf(roleName)
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(); delay(60_000) } }
    LaunchedEffect(openProperty) { openProperty?.let { nav.navigate("property/$it"); vm.consumeOpenProperty() } }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tops = listOf("home", "jobs", "properties", "account")
    val top = route in tops
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    fun go(destination: String) { nav.navigate(destination) { launchSingleTop = true } }
    fun goTop(destination: String) { nav.navigate(destination) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
    fun report(parent: Long? = null) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); go("demo-report?parent=${parent ?: -1}") }
    val back: () -> Unit = { nav.popBackStack(); Unit }
    val say: (String) -> Unit = { scope.launch { snackbar.showSnackbar(it) } }
    val labels = listOf("Home", if (role == DemoRole.technician) "Board" else "Repairs", "Properties", "You")
    val icons = listOf(Icons.Filled.Home, Icons.Filled.Handyman, Icons.Filled.HomeWork, Icons.Filled.AccountCircle)
    Scaffold(contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (top && !wide) NavigationBar {
            tops.forEachIndexed { index, item -> NavigationBarItem(selected = route == item, onClick = { goTop(item) }, icon = { Icon(icons[index], null) }, label = { Text(labels[index]) }) }
        } }) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (top && wide) NavigationRail {
                tops.forEachIndexed { index, item -> NavigationRailItem(selected = route == item, onClick = { goTop(item) }, icon = { Icon(icons[index], null) }, label = { Text(labels[index]) }) }
            }
            NavHost(nav, startDestination = "home", modifier = Modifier.weight(1f)) {
                composable("home") {
                    DemoHomeScreen(profile?.fullName ?: "You", summaries, jobs, role,
                        onRole = { vm.selectRole(it.name) }, onReport = { report() }, onJob = { go("job/$it") },
                        onProperty = { go("property/$it") }, onEmergency = { go("emergency") },
                        onPassport = { vm.exportPassport(context, it) }, onJobs = { goTop("jobs") })
                }
                composable("jobs") { DemoJobsScreen(jobs, role, onJob = { go("job/$it") }, onReport = { report() }) }
                composable("properties") {
                    DemoPage("Properties", topLevel = true) {
                        DemoTitle("The bigger picture.")
                        summaries.forEach { summary ->
                            InfoRow(Icons.Filled.HomeWork, summary.property.name, "Home health ${summary.health.score}/100 · ${summary.openJobs.size} open repairs", { go("property/${summary.property.id}") })
                            summary.checks.filter { it.state != CheckState.ok }.take(2).forEach { check -> DemoNote("${check.rule.name}: ${check.state.name.replace('_', ' ')}") }
                        }
                        PrimaryAction("Add a property", { go("edit/-1") }, icon = Icons.Filled.Add)
                        jobs.filter { it.coverage() != null }.forEach { job ->
                            InfoRow(Icons.Filled.VerifiedUser, job.title, "${job.coverage()!!.remaining(today)} warranty days remaining", { go("warranty/${job.id}") })
                        }
                    }
                }
                composable("account") {
                    var confirmDelete by remember { mutableStateOf(false) }
                    DemoPage("You", topLevel = true) {
                        DemoTitle("Your account.", profile?.fullName)
                        Text("Your view", style = MaterialTheme.typography.titleMedium)
                        DemoRole.entries.forEach { value ->
                            FilterChip(selected = role == value, onClick = { vm.selectRole(value.name) }, label = { Text(value.label) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                        }
                        DemoNote("Views are saved on this device. Technician profiles and matching are illustrative.")
                        InfoRow(Icons.Filled.AccountCircle, "Signed in", vm.localEmail)
                        TextButton(onClick = vm::signOutLocal) { Text("Sign out") }
                        InfoRow(Icons.Filled.PhotoCamera, "Photo analysis", if (vm.onlineCheckConfigured) "Online first, on-device fallback" else "Bundled on-device model · no API key required")
                        DemoNote("Live photo results use image labels plus your description. They do not invent a detection box. The sample photo has a labelled example assessment.")
                        DemoNote(Company.conceptLabel)
                        DemoNote("House: Binyamin Mellish / Pexels. Technician stock photo: Artem Podrez / Pexels. Sample tap: Zulfugar Karimov / Pexels. Inter font: SIL OFL.")
                        TextButton(onClick = { confirmDelete = true }) { Text("Delete all local data", color = MaterialTheme.colorScheme.error) }
                    }
                    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete all local data?") },
                        text = { Text("This removes properties, repairs, photos and settings from this device.") },
                        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteAllData() }) { Text("Delete data") } },
                        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep data") } })
                }
                composable("demo-report?parent={parent}", arguments = listOf(navArgument("parent") { type = NavType.LongType; defaultValue = -1L })) { stack ->
                    val parentId = stack.arguments?.getLong("parent")?.takeIf { it > 0 }
                    val parent = jobs.firstOrNull { it.id == parentId }
                    val model: DemoFlowModel = viewModel()
                    LaunchedEffect(properties, parentId) { model.start(parent?.propertyId ?: properties.firstOrNull()?.id, parentId,
                        if (parent != null) "Follow-up: ${parent.title}. " else ""); model.restore(vm) }
                    DemoReportRoute(vm, model, properties, onClose = back, onEmergency = { go("emergency") }, onSaved = { id ->
                        nav.navigate("job/$id") { popUpTo("demo-report?parent={parent}") { inclusive = true } }
                    })
                }
                composable("job/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { stack ->
                    val id = stack.arguments?.getLong("id") ?: -1
                    val job = jobs.firstOrNull { it.id == id }
                    if (job == null) MissingRepair(back) else DemoJobScreen(job, properties.firstOrNull { it.id == job.propertyId }, events[id].orEmpty(), role,
                        onBack = back, onShare = { whatsapp -> haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.sendJob(context, id, if (whatsapp) Share.Channel.whatsapp else Share.Channel.email) },
                        onComplete = { go("complete/$id") }, onWarranty = { go("warranty/$id") },
                        onStatus = { if (role == DemoRole.technician) vm.setStatus(id, it, "Demo staff update") }, onFollowup = { report(id) })
                }
                composable("complete/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { stack ->
                    val id = stack.arguments?.getLong("id") ?: -1
                    val job = jobs.firstOrNull { it.id == id }
                    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
                    if (job == null) MissingRepair(back) else if (role != DemoRole.technician) DemoPage("Technician demo", back) {
                        DemoNote("Switch to the Technician view on Home or You to complete a repair.")
                    } else DemoCompletionScreen(job, back, { days, note ->
                        if (!busy) { busy = true; error = null; scope.launch {
                            try {
                                vm.completeDemoJob(id, days, note)
                                // Room/widget work suspends; navigation must resume on Android's main dispatcher.
                                withContext(Dispatchers.Main.immediate) {
                                    nav.navigate("warranty/$id") { popUpTo("complete/{id}") { inclusive = true } }
                                }
                            }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { android.util.Log.e("PropCareCompletion", "Completion handoff failed", e); error = "Could not complete this repair. Try again." }
                            finally { busy = false }
                        } }
                    }, busy, error)
                }
                composable("warranty/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { stack ->
                    val id = stack.arguments?.getLong("id") ?: -1
                    val job = jobs.firstOrNull { it.id == id }
                    if (job == null) MissingRepair(back) else DemoWarrantyScreen(job, today, back, { report(id) })
                }
                composable("property/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { stack ->
                    val id = stack.arguments?.getLong("id") ?: -1
                    PropertyDetailScreen(PropertyDetailState(summaries.firstOrNull { it.property.id == id }, rooms[id].orEmpty(), jobs.filter { it.propertyId == id }),
                        PropertyDetailActions(onBack = back, onEdit = { go("edit/$id") }, onDelete = { vm.deleteProperty(id); back() },
                            onOpenJob = { go("job/$it") }, onSetDate = { kind, date -> vm.setCheckDate(id, kind, date) },
                            onBook = { vm.bookCheck(id, it) { newId -> go("job/$newId") } }, onAddRoom = { vm.addRoom(id, it) },
                            onRenameRoom = vm::renameRoom, onDeleteRoom = vm::deleteRoom, onExport = { vm.exportPassport(context, id) }))
                }
                composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { stack ->
                    val id = stack.arguments?.getLong("id") ?: -1
                    PropertyEditScreen(properties.firstOrNull { it.id == id }, back, { p -> vm.saveProperty(p) { back() } })
                }
                composable("emergency") { EmergencyScreen(EmergencyActions(back, { Share.dial(context, Company.phone) }, { Share.dial(context, Company.gasEmergency) })) }
            }
        }
    }
}

@Composable private fun MissingRepair(back: () -> Unit) {
    DemoPage("Repair", back) { DemoTitle("Repair unavailable.", "It may have been deleted. Return to your repairs.") }
}

@Composable private fun DemoReportRoute(vm: AppViewModel, model: DemoFlowModel, properties: List<PropertyEntity>, onClose: () -> Unit, onEmergency: () -> Unit, onSaved: (Long) -> Unit) {
    val context = LocalContext.current
    var language by rememberSaveable { mutableStateOf("en-GB") }
    var capture by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    val sample = model.state.sample
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = capture?.let(Uri::parse)
        capture = null
        if (ok && uri != null) model.load(vm, uri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) model.load(vm, uri) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(model::description)
    }
    val close: () -> Unit = { if (model.state.description.isNotBlank() || model.state.photo != null) discard = true else onClose() }
    BackHandler {
        if (model.state.step != DemoStep.describe) model.back() else close()
    }
    DemoReportScreen(model.state, properties, language, DemoReportActions(
        back = model::back, close = close,
        camera = {
            try { val target = Images.newCaptureTarget(context); capture = target.second.toString(); camera.launch(target.second) }
            catch (_: ActivityNotFoundException) { model.error("No camera app found. Choose a photo instead.") }
            catch (_: Exception) { model.error("Could not open the camera. Choose a photo instead.") }
        }, gallery = { try { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } catch (_: Exception) { model.error("Could not open the photo picker.") } },
        sample = { model.sample(vm, context) }, description = model::description, property = model::property,
        voice = { try { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, language).putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe the repair")) }
            catch (_: Exception) { model.error("Speech input is unavailable. Type the description instead.") } },
        language = { language = it }, check = { model.check(vm) }, next = model::next, choose = model::choose,
        matched = model::matched, access = model::access, title = model::title, save = { model.save(vm, onSaved) }, emergency = onEmergency,
    ))
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Leave this report?") },
        text = { Text("Your draft is saved on this device. Return to Report a repair to continue.") },
        confirmButton = { TextButton(onClick = { discard = false; model.back(); onClose() }) { Text("Leave report") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
}
