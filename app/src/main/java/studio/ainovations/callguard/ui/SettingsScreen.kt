package studio.ainovations.callguard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import studio.ainovations.callguard.BuildConfig
import studio.ainovations.callguard.domain.RuleAction
import studio.ainovations.callguard.ui.theme.BrandColors
import studio.ainovations.callguard.ui.theme.BrandFooter
import studio.ainovations.callguard.ui.theme.GlassCard
import studio.ainovations.callguard.ui.theme.irisBackdrop

/** Shared user-facing copy aligned with README product language. */
internal object CallGuardUiCopy {
    const val SCREENING_ROLE_CTA = "Set CallGuard as screening app"
    const val SCREENING_ROLE_INACTIVE_BANNER =
        "Set CallGuard as screening app before relying on these rules."
    const val CONTACTS_REPAIR_CTA = "Enable contacts access"
    const val CONTACTS_RULES_DISABLED =
        "Contact-based rules need Contacts access and stay off until you grant it. " +
            "Enable below to match against your contacts."
    const val CONTACTS_GRANTED_STATUS = "Contacts access is on."
    const val NEEDS_CONTACTS_ACCESS = "Needs contacts access"
    const val REPO_URL = "https://github.com/AInovationsStudio/callguard"
}

/**
 * Settings and permission repair. Shows the active call-screening role,
 * default region, unknown-number behavior, and contact-rule status. When
 * contacts permission is missing, it explains that contact matching is
 * disabled and offers an explicit repair action rather than silently
 * disabling it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    availableRegions: List<String>,
    onDefaultRegionChanged: (String?) -> Unit,
    onUnknownNumberActionChanged: (RuleAction) -> Unit,
    onContactMatchingToggled: (Boolean) -> Unit,
    onRepairContactsPermission: () -> Unit,
    onRequestScreeningRole: () -> Unit = {},
    onBack: () -> Unit,
    silenceSupported: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.irisBackdrop(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                ),
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(CallGuardTestTags.SETTINGS_BACK_BUTTON),
                    ) {
                        Text("Back")
                    }
                },
            )
        },
        bottomBar = { BrandFooter() },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Call-screening role", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = screeningRoleDescription(state.screeningRoleStatus),
                        modifier = Modifier.testTag(CallGuardTestTags.SETTINGS_SCREENING_ROLE_STATUS),
                    )
                    if (state.screeningRoleStatus == ScreeningRoleStatus.NotActive) {
                        Button(
                            onClick = onRequestScreeningRole,
                            modifier = Modifier.testTag(CallGuardTestTags.SETTINGS_SCREENING_ROLE_BUTTON),
                        ) {
                            Text(CallGuardUiCopy.SCREENING_ROLE_CTA)
                        }
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Default country", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Used when a number you enter doesn't include a country code.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    CountryPicker(
                        selected = state.defaultRegion,
                        options = availableRegions,
                        onSelected = onDefaultRegionChanged,
                    )
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Unknown or invalid numbers", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuleAction.entries
                            .filter { it != RuleAction.SILENCE || silenceSupported }
                            .forEach { action ->
                                FilterChip(
                                    selected = state.unknownNumberAction == action,
                                    onClick = { onUnknownNumberActionChanged(action) },
                                    label = { Text(action.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    modifier = Modifier.testTag(
                                        CallGuardTestTags.SETTINGS_UNKNOWN_ACTION_CHIP_PREFIX + action.name,
                                    ),
                                )
                            }
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Contact rules", style = MaterialTheme.typography.titleSmall)
                    if (state.contactsPermissionGranted) {
                        Text(
                            text = CallGuardUiCopy.CONTACTS_GRANTED_STATUS,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag(CallGuardTestTags.SETTINGS_CONTACTS_PERMISSION_STATUS),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Match rules against your contacts")
                            Switch(
                                checked = state.contactMatchingEnabled,
                                onCheckedChange = onContactMatchingToggled,
                                modifier = Modifier
                                    .testTag(CallGuardTestTags.SETTINGS_CONTACT_MATCHING_SWITCH)
                                    .semantics {
                                        contentDescription = "Match rules against your contacts"
                                        stateDescription = if (state.contactMatchingEnabled) {
                                            "On"
                                        } else {
                                            "Off"
                                        }
                                    },
                            )
                        }
                    } else {
                        Text(
                            text = CallGuardUiCopy.CONTACTS_RULES_DISABLED,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag(CallGuardTestTags.SETTINGS_CONTACTS_PERMISSION_WARNING),
                        )
                        Button(
                            onClick = onRepairContactsPermission,
                            modifier = Modifier.testTag(CallGuardTestTags.SETTINGS_CONTACTS_REPAIR_BUTTON),
                        ) {
                            Text(CallGuardUiCopy.CONTACTS_REPAIR_CTA)
                        }
                    }
                }
            }

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(CallGuardTestTags.SETTINGS_ABOUT_CARD),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("About", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        modifier = Modifier.testTag(CallGuardTestTags.SETTINGS_ABOUT_VERSION),
                    )
                    Text(
                        "CallGuard screens calls locally on your device. No account, analytics, or " +
                            "network access is required for screening.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Licensed under Apache-2.0.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = CallGuardUiCopy.REPO_URL,
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.InkSoft,
                    )
                }
            }
        }
    }
}

private fun screeningRoleDescription(status: ScreeningRoleStatus): String = when (status) {
    ScreeningRoleStatus.Active -> "CallGuard is the active call-screening app."
    ScreeningRoleStatus.NotActive ->
        "CallGuard is not protecting calls yet. Rules will not run until you activate it."
    ScreeningRoleStatus.Unsupported -> "Call screening is not available on this device."
}
