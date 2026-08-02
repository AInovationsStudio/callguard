package studio.ainovations.callguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import studio.ainovations.callguard.domain.RuleAction

/**
 * The guided rule wizard. Every derived field it renders — validation
 * errors, the broad-regex warning, conflict warnings, and the generated
 * examples — is computed by [CallGuardViewModel]; this composable only ever
 * lays out [state] and forwards user actions. It never calls into
 * `domain.RuleCompiler` or `domain.RuleMatcher` itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleWizardScreen(
    state: WizardState,
    availableRegions: List<String>,
    onInputChanged: (WizardField, String) -> Unit,
    onMatcherSelected: (WizardMatcherType) -> Unit,
    onActionSelected: (RuleAction) -> Unit,
    onPreviewTested: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    contactsPermissionGranted: Boolean = false,
    onRequestContactsPermission: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Create a rule", style = MaterialTheme.typography.headlineSmall)

        Text("What should CallGuard do?", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuleAction.entries.forEach { action ->
                FilterChip(
                    selected = state.action == action,
                    onClick = { onActionSelected(action) },
                    label = { Text(action.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    modifier = Modifier.testTag(CallGuardTestTags.ACTION_CHIP_PREFIX + action.name),
                )
            }
        }

        Text("How should CallGuard recognize this rule?", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(WizardMatcherType.entries.toList()) { type ->
                FilterChip(
                    selected = state.matcherType == type,
                    onClick = { onMatcherSelected(type) },
                    label = { Text(type.label) },
                    modifier = Modifier.testTag(CallGuardTestTags.MATCHER_TYPE_CHIP_PREFIX + type.name),
                )
            }
        }

        MatcherInputFields(state = state, onInputChanged = onInputChanged)

        if (state.matcherType == WizardMatcherType.EXACT_NUMBER ||
            state.matcherType == WizardMatcherType.SPECIFIC_NUMBERS
        ) {
            CountryPicker(
                selected = state.country,
                options = availableRegions,
                onSelected = { onInputChanged(WizardField.COUNTRY, it.orEmpty()) },
            )
        }
        if (state.needsRegionOptions.isNotEmpty()) {
            Text(
                "This number could be from more than one country: ${state.needsRegionOptions.joinToString(", ")}. " +
                    "Pick one above.",
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.validationError != null) {
            Text(
                text = state.validationError,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(CallGuardTestTags.WIZARD_VALIDATION_ERROR),
            )
        }
        if (state.broadRegexWarning != null) {
            Text(
                text = state.broadRegexWarning,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(CallGuardTestTags.WIZARD_BROAD_REGEX_WARNING),
            )
        }
        state.conflictWarnings.forEachIndexed { index, warning ->
            Text(
                text = warning,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(CallGuardTestTags.WIZARD_CONFLICT_WARNING_PREFIX + index),
            )
        }
        if (state.matcherType == WizardMatcherType.CONTACTS && !contactsPermissionGranted) {
            Text(
                "Contacts access is missing, so this rule will stay disabled until you grant permission.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(CallGuardTestTags.WIZARD_CONTACTS_PERMISSION_WARNING),
            )
            OutlinedButton(
                onClick = onRequestContactsPermission,
                modifier = Modifier.testTag(CallGuardTestTags.WIZARD_CONTACTS_PERMISSION_BUTTON),
            ) {
                Text("Enable contacts access")
            }
        }

        if (state.positiveExample != null || state.negativeExample != null) {
            Column {
                Text("Examples", style = MaterialTheme.typography.labelLarge)
                state.positiveExample?.let {
                    Text(
                        text = "Will match, e.g. ${it.digits}",
                        modifier = Modifier.testTag(CallGuardTestTags.WIZARD_POSITIVE_EXAMPLE),
                    )
                }
                state.negativeExample?.let {
                    Text(
                        text = "Will NOT match, e.g. ${it.digits}",
                        modifier = Modifier.testTag(CallGuardTestTags.WIZARD_NEGATIVE_EXAMPLE),
                    )
                }
            }
        } else {
            Text(
                "No example is available for this pattern yet — test a number below instead.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        OutlinedTextField(
            value = state.name,
            onValueChange = { onInputChanged(WizardField.NAME, it) },
            label = { Text("Rule name (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(CallGuardTestTags.WIZARD_NAME_FIELD),
        )

        RulePreviewScreen(
            testInput = state.previewInput,
            onTestInputChanged = { onInputChanged(WizardField.PREVIEW_INPUT, it) },
            onTestRequested = onPreviewTested,
            result = state.previewResult,
            error = state.previewError,
            stale = state.previewStale,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.testTag(CallGuardTestTags.WIZARD_CANCEL_BUTTON),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.testTag(CallGuardTestTags.WIZARD_SAVE_BUTTON),
            ) {
                Text("Save rule")
            }
        }
    }
}

@Composable
private fun MatcherInputFields(state: WizardState, onInputChanged: (WizardField, String) -> Unit) {
    when (state.matcherType) {
        WizardMatcherType.EXACT_NUMBER,
        WizardMatcherType.STARTS_WITH,
        WizardMatcherType.ENDS_WITH,
        WizardMatcherType.CONTAINS,
        WizardMatcherType.ADVANCED_PATTERN,
        -> {
            OutlinedTextField(
                value = state.rawValue,
                onValueChange = { onInputChanged(WizardField.RAW_VALUE, it) },
                label = { Text(rawValueLabel(state.matcherType)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(CallGuardTestTags.WIZARD_RAW_VALUE_FIELD),
            )
        }
        WizardMatcherType.NUMBER_RANGE -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.rangeStart,
                    onValueChange = { onInputChanged(WizardField.RANGE_START, it) },
                    label = { Text("From") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag(CallGuardTestTags.WIZARD_RANGE_START_FIELD),
                )
                OutlinedTextField(
                    value = state.rangeEnd,
                    onValueChange = { onInputChanged(WizardField.RANGE_END, it) },
                    label = { Text("To") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag(CallGuardTestTags.WIZARD_RANGE_END_FIELD),
                )
            }
        }
        WizardMatcherType.SPECIFIC_NUMBERS -> {
            OutlinedTextField(
                value = state.specificNumbersRaw,
                onValueChange = { onInputChanged(WizardField.SPECIFIC_NUMBERS, it) },
                label = { Text("One number per line") },
                modifier = Modifier.fillMaxWidth().testTag(CallGuardTestTags.WIZARD_SPECIFIC_NUMBERS_FIELD),
            )
        }
        WizardMatcherType.CONTACTS -> {
            Text("Matches any incoming call from someone already in your contacts.")
        }
    }
}

private fun rawValueLabel(type: WizardMatcherType): String = when (type) {
    WizardMatcherType.EXACT_NUMBER -> "Phone number"
    WizardMatcherType.STARTS_WITH -> "Starts with these digits"
    WizardMatcherType.ENDS_WITH -> "Ends with these digits"
    WizardMatcherType.CONTAINS -> "Contains these digits"
    WizardMatcherType.ADVANCED_PATTERN -> "Regular expression (matches the full canonical number)"
    else -> "Value"
}

/** Shared with [SettingsScreen] (default-region picker uses the same control). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountryPicker(
    selected: String?,
    options: List<String>,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().testTag(CallGuardTestTags.WIZARD_COUNTRY_FIELD),
        ) {
            Text("Country: ${selected ?: "Auto (device region)"}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Auto (device region)") },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            options.forEach { region ->
                DropdownMenuItem(
                    text = { Text(region) },
                    onClick = {
                        onSelected(region)
                        expanded = false
                    },
                )
            }
        }
    }
}
