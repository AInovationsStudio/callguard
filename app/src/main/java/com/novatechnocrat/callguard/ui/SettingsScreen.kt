package studio.ainovations.callguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import studio.ainovations.callguard.domain.RuleAction

/**
 * Settings and permission repair. Per the brief: shows the active
 * call-screening role, default region, unknown-number behavior, and
 * contact-rule status; when contacts permission is missing, explains that
 * contact matching is disabled and offers an explicit repair action rather
 * than silently disabling it.
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
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
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
                        Text("Set CallGuard as screening app")
                    }
                }
            }

            Column {
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

            Column {
                Text("Unknown or invalid numbers", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleAction.entries.forEach { action ->
                        FilterChip(
                            selected = state.unknownNumberAction == action,
                            onClick = { onUnknownNumberActionChanged(action) },
                            label = { Text(action.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }

            Column {
                Text("Contact rules", style = MaterialTheme.typography.titleSmall)
                if (state.contactsPermissionGranted) {
                    Text(
                        "Contacts access is granted.",
                        modifier = Modifier.testTag(CallGuardTestTags.SETTINGS_CONTACTS_PERMISSION_STATUS),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Match rules against your contacts")
                        Switch(checked = state.contactMatchingEnabled, onCheckedChange = onContactMatchingToggled)
                    }
                } else {
                    Text(
                        "Contacts access is not granted, so contact rules cannot run. " +
                            "CallGuard never treats this as \"match everyone.\"",
                        modifier = Modifier.testTag(CallGuardTestTags.SETTINGS_CONTACTS_PERMISSION_STATUS),
                    )
                    Button(
                        onClick = onRepairContactsPermission,
                        modifier = Modifier.testTag(CallGuardTestTags.SETTINGS_CONTACTS_REPAIR_BUTTON),
                    ) {
                        Text("Fix contacts access")
                    }
                }
            }
        }
    }
}

private fun screeningRoleDescription(status: ScreeningRoleStatus): String = when (status) {
    ScreeningRoleStatus.Active -> "CallGuard is the active call-screening app."
    ScreeningRoleStatus.NotActive -> "CallGuard is not protecting calls yet. Rules will not run until you activate it."
    ScreeningRoleStatus.Unsupported -> "Call screening is not available on this device."
}
