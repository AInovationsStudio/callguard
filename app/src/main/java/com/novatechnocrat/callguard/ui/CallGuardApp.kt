package studio.ainovations.callguard.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import studio.ainovations.callguard.data.CallGuardDatabase
import studio.ainovations.callguard.data.PreferencesRepository
import studio.ainovations.callguard.data.RuleRepository
import studio.ainovations.callguard.data.callGuardDataStore
import studio.ainovations.callguard.phone.PhoneNormalizer

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

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CallGuardEvent.OpenContactsPermissionSettings -> {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                }
            }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (state.screen) {
                is CallGuardScreen.RuleList -> RuleListScreen(
                    items = state.ruleListItems,
                    onAddRule = { viewModel.onWizardOpened() },
                    onEditRule = { id -> viewModel.onWizardOpened(id) },
                    onDeleteRule = viewModel::onRuleDeleted,
                    onToggleRule = viewModel::onRuleToggled,
                    onOpenSettings = viewModel::onSettingsOpened,
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
                )
                is CallGuardScreen.Settings -> SettingsScreen(
                    state = state.settings,
                    availableRegions = state.availableRegions,
                    onDefaultRegionChanged = viewModel::onSettingsDefaultRegionChanged,
                    onUnknownNumberActionChanged = viewModel::onUnknownNumberActionChanged,
                    onContactMatchingToggled = viewModel::onContactMatchingToggled,
                    onRepairContactsPermission = viewModel::onContactsPermissionRepairRequested,
                    onBack = viewModel::onRuleListOpened,
                )
            }
        }
    }
}

@Composable
private fun rememberDefaultCallGuardViewModel(): CallGuardViewModel {
    val context = LocalContext.current.applicationContext
    return remember {
        val database = CallGuardDatabase.build(context)
        CallGuardViewModel(
            ruleRepository = RuleRepository(database.ruleDao()),
            preferencesRepository = PreferencesRepository(context.callGuardDataStore),
            normalizer = PhoneNormalizer(deviceRegion = { deviceRegionFor(context) }),
            contactsPermissionGranted = { contactsPermissionGranted(context) },
            screeningRoleStatus = { screeningRoleStatusFor(context) },
        )
    }
}

/**
 * Best-effort device region: the SIM's network country first (matches what
 * a real incoming call would resolve against), then the configured system
 * locale. Returns null — not a guess — when neither is available, which
 * [PhoneNormalizer] already treats as "no device region" per its own
 * documented fallback order.
 */
private fun deviceRegionFor(context: Context): String? {
    val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    val simRegion = telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
    if (simRegion != null) return simRegion.uppercase()
    return context.resources.configuration.locales.get(0)?.country?.takeIf { it.isNotBlank() }?.uppercase()
}

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
