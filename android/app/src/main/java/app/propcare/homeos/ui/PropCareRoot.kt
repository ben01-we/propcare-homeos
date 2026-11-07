package app.propcare.homeos.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.propcare.homeos.AppLock
import app.propcare.homeos.AppViewModel
import app.propcare.homeos.data.refLabel
import app.propcare.homeos.data.statusEnum
import app.propcare.homeos.domain.Company
import app.propcare.homeos.ui.components.LoadingState
import app.propcare.homeos.ui.screens.AccountActions
import app.propcare.homeos.ui.screens.AccountScreen
import app.propcare.homeos.ui.screens.AccountState
import app.propcare.homeos.ui.screens.EmergencyActions
import app.propcare.homeos.ui.screens.EmergencyScreen
import app.propcare.homeos.ui.screens.HomeActions
import app.propcare.homeos.ui.screens.HomeScreen
import app.propcare.homeos.ui.screens.HomeState
import app.propcare.homeos.ui.screens.JobDetailActions
import app.propcare.homeos.ui.screens.JobDetailScreen
import app.propcare.homeos.ui.screens.JobDetailState
import app.propcare.homeos.ui.screens.JobsScreen
import app.propcare.homeos.ui.screens.JobsState
import app.propcare.homeos.ui.screens.LockScreen
import app.propcare.homeos.ui.screens.OnboardingActions
import app.propcare.homeos.ui.screens.OnboardingScreen
import app.propcare.homeos.ui.screens.PropertiesScreen
import app.propcare.homeos.ui.screens.PropertyDetailActions
import app.propcare.homeos.ui.screens.PropertyDetailScreen
import app.propcare.homeos.ui.screens.PropertyDetailState
import app.propcare.homeos.ui.screens.PropertyEditScreen
import app.propcare.homeos.ui.screens.ReportActions
import app.propcare.homeos.ui.screens.ReportScreen
import app.propcare.homeos.ui.screens.ReportStep
import app.propcare.homeos.ui.screens.ServicesActions
import app.propcare.homeos.ui.screens.ServicesScreen
import app.propcare.homeos.ui.screens.StaffBoardScreen
import app.propcare.homeos.ui.screens.StaffBoardState
import app.propcare.homeos.ui.theme.PropCareTheme
import app.propcare.homeos.util.Images
import app.propcare.homeos.util.Share
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val JOBS = "jobs"
    const val PROPERTIES = "properties"
    const val ACCOUNT = "account"
    const val BOARD = "board"
    const val SERVICES = "services"
    const val EMERGENCY = "emergency"
    const val REPORT = "report?propertyId={propertyId}"
    const val JOB = "job/{id}"
    const val PROPERTY = "property/{id}"
    const val EDIT = "edit/{id}"
    fun report(propertyId: Long? = null) = "report?propertyId=${propertyId ?: -1}"
    fun job(id: Long) = "job/$id"
    fun property(id: Long) = "property/$id"
    fun edit(id: Long) = "edit/$id"
}

private data class TopDest(val route: String, val label: String, val icon: ImageVector)

/**
 * The app's root: theme, lock, onboarding, then the main navigation.
 * MainActivity calls this (contract logged on the coord board).
 */
@Composable
fun PropCareRoot(vm: AppViewModel, locked: Boolean, onUnlock: () -> Unit) {
    PropCareTheme {
        val ready by vm.ready.collectAsStateWithLifecycle()
        val profile by vm.profile.collectAsStateWithLifecycle()
        val signedIn by vm.signedIn.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
        when {
            locked -> LockScreen(onUnlock)
            !ready -> Surface(Modifier.fillMaxSize()) { LoadingState() }
            !signedIn || profile == null -> Box(Modifier.fillMaxSize()) {
                var loading by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                app.propcare.homeos.ui.demo.LocalLoginScreen(vm.hasLocalAccount, vm.localEmail, loading, error) { name, email, password ->
                    if (!loading) {
                        loading = true; error = null
                        scope.launch {
                            try {
                                if (!vm.hasLocalAccount) vm.registerLocal(name, email, password)
                                else when (val result = vm.signInLocal(email, password)) {
                                    app.propcare.homeos.data.Settings.PinResult.Ok -> Unit
                                    app.propcare.homeos.data.Settings.PinResult.Wrong -> error = "Email or password is incorrect. Try again."
                                    is app.propcare.homeos.data.Settings.PinResult.Locked -> error = "Try again in ${result.seconds} seconds."
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "Could not sign in. Try again." }
                            finally { loading = false }
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            }
            else -> app.propcare.homeos.ui.demo.DemoApp(vm, snackbar)
        }
    }
}

@Composable
private fun Onboarding(vm: AppViewModel) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    OnboardingScreen(
        notificationsAllowed = allowed,
        actions = OnboardingActions(
            onSave = vm::saveProfile,
            onLoadSample = vm::seedSample,
            onAllowNotifications = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) },
        ),
    )
}

private fun notificationsAllowed(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
private fun MainNav(vm: AppViewModel, snackbar: SnackbarHostState) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val say: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    val profile by vm.profile.collectAsStateWithLifecycle()
    val properties by vm.properties.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val staffMode by vm.staffMode.collectAsStateWithLifecycle()
    val appLock by vm.appLock.collectAsStateWithLifecycle()
    val hasPin by vm.hasStaffPin.collectAsStateWithLifecycle()
    val openProperty by vm.openProperty.collectAsStateWithLifecycle()
    val names = properties.associate { it.id to it.name }

    LaunchedEffect(openProperty) {
        openProperty?.let { nav.navigate(Routes.property(it)) { launchSingleTop = true }; vm.consumeOpenProperty() }
    }

    val tops = buildList {
        add(TopDest(Routes.HOME, "Home", Icons.Filled.Home))
        add(TopDest(Routes.JOBS, "Jobs", Icons.Filled.Handyman))
        add(TopDest(Routes.PROPERTIES, "Properties", Icons.Filled.HomeWork))
        if (staffMode) add(TopDest(Routes.BOARD, "Board", Icons.Filled.Dashboard))
        add(TopDest(Routes.ACCOUNT, "Account", Icons.Filled.AccountCircle))
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTop = tops.any { it.route == route }
    val showFab = route == Routes.HOME || route == Routes.JOBS
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val goTop: (String) -> Unit = { r ->
        nav.navigate(r) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val report = { nav.navigate(Routes.report()) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (showFab && !wide) {
                ExtendedFloatingActionButton(
                    onClick = report, icon = { Icon(Icons.Filled.AddAPhoto, null) }, text = { Text("Report issue") },
                    containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
        },
        bottomBar = {
            if (isTop && !wide) {
                NavigationBar {
                    tops.forEach { t ->
                        NavigationBarItem(selected = route == t.route, onClick = { goTop(t.route) }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                    }
                }
            }
        },
    ) { inner ->
        Row(Modifier.fillMaxSize().padding(inner)) {
            if (isTop && wide) {
                NavigationRail(header = {
                    if (showFab) FloatingActionButton(
                        onClick = report, containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary,
                    ) { Icon(Icons.Filled.AddAPhoto, contentDescription = "Report issue") }
                }) {
                    tops.forEach { t -> NavigationRailItem(selected = route == t.route, onClick = { goTop(t.route) }, icon = { Icon(t.icon, null) }, label = { Text(t.label) }) }
                }
            }
            NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.weight(1f)) {
                composable(Routes.HOME) {
                    HomeScreen(
                        HomeState(profile?.fullName?.substringBefore(' ') ?: "", summaries, jobs.filter { it.statusEnum.isOpen }, names),
                        HomeActions(
                            onEmergency = { nav.navigate(Routes.EMERGENCY) },
                            onOpenJob = { nav.navigate(Routes.job(it)) },
                            onOpenProperty = { nav.navigate(Routes.property(it)) },
                            onAddProperty = { nav.navigate(Routes.edit(0)) },
                            onBookCheck = { pid, kind -> vm.bookCheck(pid, kind) { id -> nav.navigate(Routes.job(id)) } },
                            onLoadSample = vm::seedSample,
                            onServices = { nav.navigate(Routes.SERVICES) },
                        ),
                    )
                }
                composable(Routes.JOBS) {
                    JobsScreen(JobsState(jobs, names), onOpenJob = { nav.navigate(Routes.job(it)) }, onReport = report)
                }
                composable(Routes.PROPERTIES) {
                    PropertiesScreen(summaries, onOpen = { nav.navigate(Routes.property(it)) }, onAdd = { nav.navigate(Routes.edit(0)) })
                }
                composable(Routes.BOARD) {
                    StaffBoardScreen(StaffBoardState(jobs, names), onUpdate = vm::staffUpdate, onOpenJob = { nav.navigate(Routes.job(it)) })
                }
                composable(Routes.ACCOUNT) {
                    AccountScreen(
                        AccountState(profile, appLock, AppLock.available(context), staffMode, hasPin, vm.onlineCheckConfigured),
                        AccountActions(
                            onSaveProfile = vm::saveProfile,
                            onAppLock = vm::setAppLock,
                            onSetPin = vm::setStaffPin,
                            onEnterPin = { pin -> vm.checkStaffPin(pin) },
                            onLeaveStaff = { vm.leaveStaffMode(); goTop(Routes.ACCOUNT) },
                            onDeleteAll = { vm.deleteAllData() },
                            onServices = { nav.navigate(Routes.SERVICES) },
                        ),
                    )
                }
                composable(Routes.SERVICES) {
                    ServicesScreen(
                        ServicesActions(
                            onBack = { nav.popBackStack() },
                            onCall = { Share.dial(context, Company.phone) },
                            onWhatsApp = { Share.send(context, Share.Channel.whatsapp, "PropCare enquiry", "Hello PropCare, ", null) },
                            onEmail = { Share.send(context, Share.Channel.email, "PropCare enquiry", "Hello PropCare,\n\n", null) },
                        ),
                    )
                }
                composable(Routes.EMERGENCY) {
                    EmergencyScreen(
                        EmergencyActions(
                            onBack = { nav.popBackStack() },
                            onCallPropCare = { Share.dial(context, Company.phone) },
                            onCallGas = { Share.dial(context, Company.gasEmergency) },
                        ),
                    )
                }
                composable(Routes.JOB, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    val id = e.arguments?.getLong("id") ?: 0L
                    val job = jobs.firstOrNull { it.id == id }
                    JobDetailScreen(
                        JobDetailState(job, job?.let { j -> properties.firstOrNull { it.id == j.propertyId } }, events[id].orEmpty()),
                        JobDetailActions(
                            onBack = { nav.popBackStack() },
                            onCancel = { vm.cancelJob(id) },
                            onWhatsApp = { vm.sendJob(context, id, Share.Channel.whatsapp) },
                            onEmail = { vm.sendJob(context, id, Share.Channel.email) },
                            onCall = { Share.dial(context, Company.phone) },
                            onEmergency = { nav.navigate(Routes.EMERGENCY) },
                        ),
                    )
                }
                composable(Routes.PROPERTY, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    val id = e.arguments?.getLong("id") ?: 0L
                    PropertyDetailScreen(
                        PropertyDetailState(summaries.firstOrNull { it.property.id == id }, rooms[id].orEmpty(), jobs.filter { it.propertyId == id }),
                        PropertyDetailActions(
                            onBack = { nav.popBackStack() },
                            onEdit = { nav.navigate(Routes.edit(id)) },
                            onDelete = { vm.deleteProperty(id); nav.popBackStack() },
                            onSetDate = { kind, date -> vm.setCheckDate(id, kind, date) },
                            onBook = { kind -> vm.bookCheck(id, kind) { jobId -> nav.navigate(Routes.job(jobId)) } },
                            onAddRoom = { vm.addRoom(id, it) },
                            onRenameRoom = vm::renameRoom,
                            onDeleteRoom = vm::deleteRoom,
                            onExport = { vm.exportPassport(context, id) },
                            onOpenJob = { nav.navigate(Routes.job(it)) },
                        ),
                    )
                }
                composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    val id = e.arguments?.getLong("id") ?: 0L
                    val initial = properties.firstOrNull { it.id == id }
                    if (id != 0L && initial == null) {
                        LoadingState()
                    } else {
                        PropertyEditScreen(initial, onBack = { nav.popBackStack() }, onSave = { p ->
                            vm.saveProperty(p) { newId ->
                                nav.popBackStack()
                                // A new property opens its page, unless we came from the report flow.
                                if (id == 0L && nav.currentDestination?.route != Routes.REPORT) nav.navigate(Routes.property(newId))
                            }
                        })
                    }
                }
                composable(Routes.REPORT, arguments = listOf(navArgument("propertyId") { type = NavType.LongType; defaultValue = -1L })) { e ->
                    val pid = e.arguments?.getLong("propertyId")?.takeIf { it > 0 } ?: properties.singleOrNull()?.id
                    ReportRoute(vm, pid, nav, say)
                }
            }
        }
    }
}

@Composable
private fun ReportRoute(vm: AppViewModel, initialPropertyId: Long?, nav: NavHostController, say: (String) -> Unit) {
    val model: ReportFlowModel = viewModel()
    val context = LocalContext.current
    val properties by vm.properties.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { model.start(initialPropertyId, vm.onlineCheckConfigured) }
    // A property added from inside the flow is picked automatically.
    LaunchedEffect(properties.size) { if (model.state.propertyId == null && properties.size == 1) model.update { it.copy(propertyId = properties.first().id) } }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = model.captureUri
        if (ok && uri != null) model.loadPhoto(vm, uri)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) model.loadPhoto(vm, uri) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            model.update { s -> s.copy(description = listOf(s.description.trim(), text.trim()).filter { it.isNotEmpty() }.joinToString(" ").take(500)) }
        }
    }
    val close: () -> Unit = { vm.clearCaptures(); nav.popBackStack() }
    val savedId = model.savedJobId
    val state = model.state.copy(properties = properties, savedJobRef = savedId?.let { id -> jobs.firstOrNull { it.id == id }?.refLabel })

    ReportScreen(
        state,
        ReportActions(
            onClose = close,
            onBackStep = model::back,
            onTakePhoto = {
                val (_, uri) = Images.newCaptureTarget(context)
                model.captureUri = uri
                try { camera.launch(uri) } catch (_: ActivityNotFoundException) { say("No camera app found. Choose a photo instead.") }
            },
            onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemovePhoto = { model.setPhoto(null) },
            onVoice = {
                val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, model.state.voiceLanguage.tag)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe the issue")
                try { voice.launch(i) } catch (_: ActivityNotFoundException) { say("Voice input is not available on this phone. Type the description instead.") }
            },
            onVoiceLanguage = { l -> model.update { it.copy(voiceLanguage = l) } },
            onDescription = { d -> model.update { it.copy(description = d) } },
            onProperty = { p -> model.update { it.copy(propertyId = p) } },
            onAddProperty = { nav.navigate(Routes.edit(0)) },
            onCheck = { model.check(vm) },
            onContinue = { model.update { it.copy(step = ReportStep.details) } },
            onTitle = { t -> model.update { it.copy(title = t) } },
            onTrade = { t -> model.update { it.copy(trade = t) } },
            onUrgency = { u -> model.update { it.copy(urgency = u) } },
            onAccess = { a -> model.update { it.copy(accessNotes = a) } },
            onTimes = { t -> model.update { it.copy(preferredTimes = t) } },
            onSave = { model.save(vm) },
            onEmergency = { nav.navigate(Routes.EMERGENCY) },
            onWhatsApp = { savedId?.let { vm.sendJob(context, it, Share.Channel.whatsapp) } },
            onEmail = { savedId?.let { vm.sendJob(context, it, Share.Channel.email) } },
            onViewJob = {
                savedId?.let { id ->
                    vm.clearCaptures()
                    nav.navigate(Routes.job(id)) { popUpTo(Routes.REPORT) { inclusive = true } }
                }
            },
        ),
    )
}
