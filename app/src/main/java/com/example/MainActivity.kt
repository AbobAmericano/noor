package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ai.AiChatMode
import com.example.data.ai.configureAppCheck
import com.example.data.preferences.NoorPreferencesRepository
import com.example.data.preferences.NoorSettingsState
import com.example.data.repository.InspirationRepository
import com.example.data.repository.NoorFirestoreRepository
import com.example.notifications.NoorNotificationHelper
import com.example.ui.NoorAiViewModel
import com.example.ui.NoorViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.NoorSettingsSheet
import com.example.ui.components.SadaqahSupportDialog
import com.example.ui.screens.ArchiveResearchScreen
import com.example.ui.screens.AskAiScreen
import com.example.ui.screens.SalawatScreen
import com.example.ui.screens.SolatQiblaScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.NoorTheme
import com.example.widget.NoorDailyWidgetProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

enum class NoorRoute(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TODAY("today", "Today", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    SOLAT("solat", "Solat", Icons.Filled.Explore, Icons.Outlined.Explore),
    SALAWAT("salawat", "Salawat", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    ASK_AI("ask_ai", "Ask AI", Icons.Filled.QuestionAnswer, Icons.Outlined.QuestionAnswer),
    ARCHIVE("archive", "Archive", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook)
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        NoorNotificationHelper.ensureNotificationChannels(this)

        setContent {
            val context = LocalContext.current
            val prefsRepo = remember(context) { NoorPreferencesRepository(context) }
            val settings by prefsRepo.settingsFlow.collectAsStateWithLifecycle(initialValue = NoorSettingsState())

            NoorTheme(darkTheme = settings.isDarkMode) {
                AppNavigation(prefsRepo = prefsRepo)
            }
        }
    }
}

@Composable
fun AppNavigation(
    prefsRepo: NoorPreferencesRepository,
    auth: FirebaseAuth = Firebase.auth
) {
    var user by remember(auth) { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            user = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        user = auth.currentUser
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val activeUser = user
    if (activeUser == null) {
        AuthScreen(
            onAuthSuccess = { user = auth.currentUser }
        )
    } else {
        key(activeUser.uid) {
            MainNoorShell(
                currentUserId = activeUser.uid,
                userEmail = activeUser.email,
                prefsRepo = prefsRepo
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNoorShell(
    currentUserId: String,
    userEmail: String?,
    prefsRepo: NoorPreferencesRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: NoorViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                NoorViewModel(
                    firestoreRepository = NoorFirestoreRepository(db, Firebase.auth),
                    inspirationRepository = InspirationRepository(),
                    preferencesRepository = prefsRepo,
                    currentUserId = currentUserId,
                    onSyncWidget = { NoorDailyWidgetProvider.updateAllWidgets(app) },
                    onReschedulePrayers = { schedule, enabled ->
                        NoorNotificationHelper.schedulePrayerReminders(app, schedule, enabled)
                    },
                    onRescheduleDailyQuote = { enabled ->
                        NoorNotificationHelper.scheduleDailyInspiration(app, enabled)
                    }
                )
            }
        }
    )

    val aiViewModel: NoorAiViewModel = viewModel(
        key = "ai_$currentUserId"
    )

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val savedVerses by viewModel.savedVerses.collectAsStateWithLifecycle()
    val todayDhikrLog by viewModel.todayDhikrLog.collectAsStateWithLifecycle()
    val dhikrProfile by viewModel.dhikrProfile.collectAsStateWithLifecycle()
    val currentQuote by viewModel.currentQuote.collectAsStateWithLifecycle()
    val isLoadingQuote by viewModel.isLoadingQuote.collectAsStateWithLifecycle()
    val quoteFilter by viewModel.quoteFilter.collectAsStateWithLifecycle()
    val solatSchedule by viewModel.solatSchedule.collectAsStateWithLifecycle()
    val isLoadingSolat by viewModel.isLoadingSolat.collectAsStateWithLifecycle()
    val showSadaqahDialog by viewModel.showSadaqahDialog.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val activeRoute = backStackEntry?.destination?.route ?: NoorRoute.TODAY.route

    val snackbarHostState = remember { SnackbarHostState() }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var solatSubTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Noor  •  النُّور",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    actions = {
                        IconButton(
                            onClick = { viewModel.openSadaqahDialog() },
                            modifier = Modifier.testTag("open_sadaqah_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolunteerActivism,
                                contentDescription = "Give Sadaqah to support developer",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleDarkMode(!settings.isDarkMode) },
                            modifier = Modifier.testTag("toggle_theme_button")
                        ) {
                            Icon(
                                imageVector = if (settings.isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (settings.isDarkMode) "Switch to Daylight Mode" else "Switch to Night Reading Mode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.testTag("open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Open settings and notifications",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NoorRoute.entries.forEach { tab ->
                            val selected = activeRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            popUpTo(NoorRoute.TODAY.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NoorRoute.entries.forEach { tab ->
                            val selected = activeRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            popUpTo(NoorRoute.TODAY.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = NoorRoute.TODAY.route
                    ) {
                        composable(NoorRoute.TODAY.route) {
                            TodayScreen(
                                currentQuote = currentQuote,
                                isLoadingQuote = isLoadingQuote,
                                quoteFilter = quoteFilter,
                                savedVersesState = savedVerses,
                                solatSchedule = solatSchedule,
                                todayDhikrLog = todayDhikrLog,
                                settings = settings,
                                onSelectFilter = viewModel::setQuoteFilter,
                                onRefreshQuote = { viewModel.refreshDailyQuote() },
                                onSelectCuratedQuote = viewModel::selectCuratedQuote,
                                onToggleSaveQuote = { viewModel.toggleSaveCurrentQuote() },
                                onQuickIncrementSalawat = { viewModel.incrementSalawat(1L) },
                                onAskAiAboutQuote = { quote ->
                                    aiViewModel.sendChatMessage(
                                        rawMessage = "Please reflect on this ${quote.sourceType.displayLabel} (${quote.reference}): \"${quote.translation}\" — share its context and practical takeaway.",
                                        latitude = solatSchedule.latitude,
                                        longitude = solatSchedule.longitude,
                                        locationLabel = solatSchedule.locationLabel,
                                        overrideMode = AiChatMode.ISLAMIC_COMPANION
                                    )
                                    navController.navigate(NoorRoute.ASK_AI.route) {
                                        popUpTo(NoorRoute.TODAY.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onNavigateToSolat = {
                                    solatSubTab = 0
                                    navController.navigate(NoorRoute.SOLAT.route) {
                                        popUpTo(NoorRoute.TODAY.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onNavigateToMosques = {
                                    solatSubTab = 2
                                    navController.navigate(NoorRoute.SOLAT.route) {
                                        popUpTo(NoorRoute.TODAY.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onNavigateToSalawat = {
                                    navController.navigate(NoorRoute.SALAWAT.route) {
                                        popUpTo(NoorRoute.TODAY.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        composable(NoorRoute.SOLAT.route) {
                            SolatQiblaScreen(
                                solatSchedule = solatSchedule,
                                isLoadingSolat = isLoadingSolat,
                                todayDhikrLog = todayDhikrLog,
                                aiViewModel = aiViewModel,
                                initialSubTab = solatSubTab,
                                onTogglePrayerCompleted = viewModel::togglePrayerCompleted,
                                onSelectPreset = viewModel::applyRegionalPreset,
                                onDetectedLocation = viewModel::applyDetectedLocation,
                                onSelectMethod = viewModel::setCalculationMethod,
                                onAskAiWithPrompt = { prompt, mode ->
                                    aiViewModel.sendChatMessage(
                                        rawMessage = prompt,
                                        latitude = solatSchedule.latitude,
                                        longitude = solatSchedule.longitude,
                                        locationLabel = solatSchedule.locationLabel,
                                        overrideMode = mode
                                    )
                                    navController.navigate(NoorRoute.ASK_AI.route) {
                                        popUpTo(NoorRoute.TODAY.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onFeedback = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                        }

                        composable(NoorRoute.SALAWAT.route) {
                            SalawatScreen(
                                todayLog = todayDhikrLog,
                                profile = dhikrProfile,
                                settings = settings,
                                onSelectPhrase = viewModel::setActiveSalawatPhrase,
                                onIncrementSalawat = viewModel::incrementSalawat,
                                onResetToday = viewModel::resetTodaySalawat,
                                onSetTarget = viewModel::setDailySalawatTarget,
                                onTogglePrayer = viewModel::togglePrayerCompleted,
                                onToggleHaptics = viewModel::toggleDhikrHaptics
                            )
                        }

                        composable(NoorRoute.ASK_AI.route) {
                            val chatUiState by aiViewModel.chatUiState.collectAsStateWithLifecycle()
                            AskAiScreen(
                                uiState = chatUiState,
                                solatSchedule = solatSchedule,
                                onDraftChange = aiViewModel::updateDraftInput,
                                onSelectMode = aiViewModel::selectChatMode,
                                onSendMessage = { text ->
                                    aiViewModel.sendChatMessage(
                                        rawMessage = text,
                                        latitude = solatSchedule.latitude,
                                        longitude = solatSchedule.longitude,
                                        locationLabel = solatSchedule.locationLabel
                                    )
                                },
                                onResetChat = aiViewModel::resetChat
                            )
                        }

                        composable(NoorRoute.ARCHIVE.route) {
                            ArchiveResearchScreen(
                                savedVersesState = savedVerses,
                                onUpdateNote = viewModel::updateVerseReflection,
                                onDeleteVerse = viewModel::deleteSavedVerse
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        NoorSettingsSheet(
            userEmail = userEmail,
            settings = settings,
            currentQuote = currentQuote,
            onDismiss = { showSettingsSheet = false },
            onToggleDarkMode = viewModel::toggleDarkMode,
            onToggleDailyQuoteNotif = viewModel::toggleDailyQuoteNotifications,
            onTogglePrayerReminders = viewModel::togglePrayerReminders,
            onToggleDhikrHaptics = viewModel::toggleDhikrHaptics,
            onSyncWidget = {
                viewModel.syncWidgetManually()
                showSettingsSheet = false
            },
            onOpenSadaqah = {
                showSettingsSheet = false
                viewModel.openSadaqahDialog()
            },
            onSignOut = {
                showSettingsSheet = false
                signOutUser(credentialManager, scope)
            },
            onNotifyFeedback = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    if (showSadaqahDialog) {
        SadaqahSupportDialog(
            onCloseWithBlessing = {
                viewModel.dismissSadaqahDialog("Ameen! JazakAllahu Khayran for your du'a.")
            },
            onCompleted = { message ->
                viewModel.dismissSadaqahDialog(message)
            }
        )
    }
}
