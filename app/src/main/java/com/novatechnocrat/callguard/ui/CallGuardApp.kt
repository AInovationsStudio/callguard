package studio.ainovations.callguard.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import studio.ainovations.callguard.data.CallGuardDatabase
import studio.ainovations.callguard.data.PreferencesRepository
import studio.ainovations.callguard.data.RuleRepository
import studio.ainovations.callguard.data.callGuardDataStore
import studio.ainovations.callguard.phone.PhoneNormalizer
import studio.ainovations.callguard.screening.deviceRegionFor
import studio.ainovations.callguard.ui.theme.CallGuardTheme

/**
 * The app's Compose entry point. [viewModel] defaults to a real,
 * `Context`-backed instance for production (via [rememberDefaultCallGuardViewModel]);
 * `RuleWizardTest` overrides it with one wired to an in-memory database so a
 * test never touches real disk state.
 */
@Composable
fun CallGuardApp(viewModel: CallGuardViewModel = rememberDefaultCallGuardViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissionState()
    }
    val screeningRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshPermissionState()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CallGuardEvent.RequestContactsPermission ->
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                is CallGuardEvent.RequestScreeningRole -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val roleManager = context.getSystemService(RoleManager::class.java)
                        if (roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true) {
                            screeningRoleLauncher.launch(
                                roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
                            )
                        }
                    }
                }
            }
        }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CallGuardTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (state.screen) {
                is CallGuardScreen.RuleList -> RuleListScreen(
                    items = state.ruleListItems,
                    onAddRule = { viewModel.onWizardOpened() },
                    onEditRule = { id -> viewModel.onWizardOpened(id) },
                    onDeleteRule = viewModel::onRuleDeleted,
                    onToggleRule = viewModel::onRuleToggled,
                    onOpenSettings = viewModel::onSettingsOpened,
                    screeningRoleStatus = state.settings.screeningRoleStatus,
                    contactsPermissionGranted = state.settings.contactsPermissionGranted,
                    onRequestScreeningRole = viewModel::onScreeningRoleRequested,
                )
                is CallGuardScreen.Wizard -> RuleWizardScreen(
                    state = state.wizard,
                    availableRegions = state.availableRegions,
                    onInputChanged = viewModel::onWizardInputChanged,
                    onMatcherSelected = viewModel::onMatcherSelected,
                    onActionSelected = viewModel::onActionSelected,
                    onPreviewTested = viewModel::onPreviewTested,
                    onSave = viewModel::onRuleSaved,
                    onCancel = viewModel::onWizardCancelled,
                    contactsPermissionGranted = state.settings.contactsPermissionGranted,
                    onRequestContactsPermission = viewModel::onContactsPermissionRepairRequested,
                )
                is CallGuardScreen.Settings -> SettingsScreen(
                    state = state.settings,
                    availableRegions = state.availableRegions,
                    onDefaultRegionChanged = viewModel::onSettingsDefaultRegionChanged,
                    onUnknownNumberActionChanged = viewModel::onUnknownNumberActionChanged,
                    onContactMatchingToggled = viewModel::onContactMatchingToggled,
                    onRepairContactsPermission = viewModel::onContactsPermissionRepairRequested,
                    onRequestScreeningRole = viewModel::onScreeningRoleRequested,
                    onBack = viewModel::onRuleListOpened,
                )
            }
        }
    }
}

@Composable
private fun rememberDefaultCallGuardViewModel(): CallGuardViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val database = CallGuardDatabase.build(context)
                return CallGuardViewModel(
                    ruleRepository = RuleRepository(database.ruleDao()),
                    preferencesRepository = PreferencesRepository(context.callGuardDataStore),
                    normalizer = PhoneNormalizer(deviceRegion = { deviceRegionFor(context) }),
                    contactsPermissionGranted = { contactsPermissionGranted(context) },
                    screeningRoleStatus = { screeningRoleStatusFor(context) },
                ) as T
            }
        },
    )
}

/**
 * Best-effort device region: the SIM's network country first (matches what
 * a real incoming call would resolve against), then the configured system
 * locale. Returns null — not a guess — when neither is available, which
 * [PhoneNormalizer] already treats as "no device region" per its own
 * documented fallback order.
 */
private fun contactsPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

/**
 * Never returns [ScreeningRoleStatus.Active] unless [RoleManager] itself
 * confirms it — API < 29 and "role unavailable on this device" both report
 * [ScreeningRoleStatus.Unsupported], never a silent guess at "active."
 */
private fun screeningRoleStatusFor(context: Context): ScreeningRoleStatus {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ScreeningRoleStatus.Unsupported
    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
        ?: return ScreeningRoleStatus.Unsupported
    if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return ScreeningRoleStatus.Unsupported
    return if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
        ScreeningRoleStatus.Active
    } else {
        ScreeningRoleStatus.NotActive
    }
}
