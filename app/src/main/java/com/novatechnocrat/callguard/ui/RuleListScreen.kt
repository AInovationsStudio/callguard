package studio.ainovations.callguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The enabled-rule list. Per the brief: each row shows the action, a
 * human-readable matcher description, enabled state, priority, and
 * edit/delete controls; advanced matcher syntax (e.g. a raw regex) is folded
 * into [RuleListItem.matcherDescription] rather than shown as its own field,
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
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("CallGuard") },
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
                modifier = Modifier.testTag(CallGuardTestTags.ADD_RULE_BUTTON),
            ) {
                Text("+")
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(modifier = Modifier.padding(padding).padding(24.dp)) {
                Text("No rules yet. Tap + to block or allow your first number or pattern.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(items, key = { it.id }) { item ->
                    RuleRow(item, onEditRule, onDeleteRule, onToggleRule)
                }
            }
        }
    }
}

@Composable
private fun RuleRow(
    item: RuleListItem,
    onEditRule: (String) -> Unit,
    onDeleteRule: (String) -> Unit,
    onToggleRule: (String, Boolean) -> Unit,
) {
    Card(
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
                Text(text = "Priority ${item.priority}", style = MaterialTheme.typography.labelSmall)
            }
            Switch(
                checked = item.enabled,
                onCheckedChange = { onToggleRule(item.id, it) },
                modifier = Modifier.testTag(CallGuardTestTags.RULE_TOGGLE_PREFIX + item.id),
            )
            TextButton(onClick = { onEditRule(item.id) }) {
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
