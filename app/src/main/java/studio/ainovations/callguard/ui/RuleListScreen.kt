package studio.ainovations.callguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import studio.ainovations.callguard.ui.theme.BrandColors
import studio.ainovations.callguard.ui.theme.GlassCard
import studio.ainovations.callguard.ui.theme.irisBackdrop

/**
 * The enabled-rule list. Each row shows the action, a human-readable matcher
 * description, enabled state, priority, and edit/delete controls. Advanced
 * matcher syntax (e.g. a raw regex) is folded into
 * [RuleListItem.matcherDescription] rather than shown as its own field,
 * keeping the guided presentation primary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    items: List<RuleListItem>,
    onAddRule: () -> Unit,
    onEditRule: (String) -> Unit,
    onDeleteRule: (String) -> Unit,
    onToggleRule: (String, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    screeningRoleStatus: ScreeningRoleStatus = ScreeningRoleStatus.Unsupported,
    contactsPermissionGranted: Boolean = false,
    onRequestScreeningRole: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier.irisBackdrop(),
        topBar = {
            TopAppBar(
                title = { Text("CallGuard") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    TextButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag(CallGuardTestTags.SETTINGS_BUTTON),
                    ) {
                        Text("Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddRule,
                modifier = Modifier
                    .testTag(CallGuardTestTags.ADD_RULE_BUTTON)
                    .semantics { contentDescription = "Add rule" },
                containerColor = BrandColors.Ink,
                contentColor = BrandColors.Canvas,
            ) {
                AddSymbol()
            }
        },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            if (screeningRoleStatus != ScreeningRoleStatus.Active) {
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .testTag(CallGuardTestTags.RULE_LIST_SCREENING_STATUS),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = when (screeningRoleStatus) {
                                    ScreeningRoleStatus.NotActive ->
                                        "CallGuard is not protecting calls yet."
                                    ScreeningRoleStatus.Unsupported ->
                                        "Call screening is not available on this device."
                                    ScreeningRoleStatus.Active -> ""
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (screeningRoleStatus == ScreeningRoleStatus.NotActive) {
                                Text(CallGuardUiCopy.SCREENING_ROLE_INACTIVE_BANNER)
                                Button(onClick = onRequestScreeningRole) {
                                    Text(CallGuardUiCopy.SCREENING_ROLE_CTA)
                                }
                            }
                        }
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    Text(
                        "No rules yet. Add a number or pattern to decide which calls should be blocked, silenced, or allowed.",
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                items(items, key = { it.id }) { item ->
                    RuleRow(
                        item = item,
                        contactsPermissionGranted = contactsPermissionGranted,
                        onEditRule = onEditRule,
                        onDeleteRule = { pendingDeleteId = it },
                        onToggleRule = onToggleRule,
                    )
                }
            }
        }
    }
    pendingDeleteId?.let { id ->
        val item = items.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete rule?") },
            text = { Text("This will remove ${item?.name ?: "this rule"} and stop applying it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRule(id)
                        pendingDeleteId = null
                    },
                    modifier = Modifier.testTag(CallGuardTestTags.RULE_DELETE_CONFIRM_BUTTON),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun RuleRow(
    item: RuleListItem,
    contactsPermissionGranted: Boolean,
    onEditRule: (String) -> Unit,
    onDeleteRule: (String) -> Unit,
    onToggleRule: (String, Boolean) -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(CallGuardTestTags.RULE_LIST_ITEM_PREFIX + item.id),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "${item.actionLabel} — ${item.name}", style = MaterialTheme.typography.titleSmall)
                Text(text = item.matcherDescription, style = MaterialTheme.typography.bodySmall)
                if (item.priority != 0) {
                    Text(text = "Priority ${item.priority}", style = MaterialTheme.typography.labelSmall)
                }
                if (item.requiresContactsPermission && !contactsPermissionGranted) {
                    Text(
                        text = CallGuardUiCopy.NEEDS_CONTACTS_ACCESS,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Switch(
                checked = item.enabled,
                onCheckedChange = { onToggleRule(item.id, it) },
                modifier = Modifier
                    .testTag(CallGuardTestTags.RULE_TOGGLE_PREFIX + item.id)
                    .semantics {
                        contentDescription = "${item.name} rule"
                        stateDescription = if (item.enabled) "On" else "Off"
                    },
            )
            TextButton(
                onClick = { onEditRule(item.id) },
                modifier = Modifier.testTag(CallGuardTestTags.RULE_EDIT_PREFIX + item.id),
            ) {
                Text("Edit")
            }
            TextButton(
                onClick = { onDeleteRule(item.id) },
                modifier = Modifier.testTag(CallGuardTestTags.RULE_DELETE_PREFIX + item.id),
            ) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun AddSymbol() {
    val color = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = Modifier.size(24.dp)) {
        drawLine(color, start = androidx.compose.ui.geometry.Offset(size.width / 2f, 4f), end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height - 4f), strokeWidth = 2.dp.toPx())
        drawLine(color, start = androidx.compose.ui.geometry.Offset(4f, size.height / 2f), end = androidx.compose.ui.geometry.Offset(size.width - 4f, size.height / 2f), strokeWidth = 2.dp.toPx())
    }
}
