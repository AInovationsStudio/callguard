package studio.ainovations.callguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import studio.ainovations.callguard.domain.MatchResult

private const val RULE_EXPLANATION_PREFIX = "Rule '"
private const val RULE_NAME_END_MARKER = "' ("

internal data class ParsedRuleExplanation(
    val name: String,
    val ruleId: String,
    val action: String,
    val detail: String,
)

internal fun parseRuleExplanation(explanation: String): ParsedRuleExplanation? {
    if (!explanation.startsWith(RULE_EXPLANATION_PREFIX)) return null
    val nameEnd = explanation.lastIndexOf(RULE_NAME_END_MARKER)
    if (nameEnd <= RULE_EXPLANATION_PREFIX.length) return null
    val name = explanation.substring(RULE_EXPLANATION_PREFIX.length, nameEnd)
    val afterName = explanation.substring(nameEnd + RULE_NAME_END_MARKER.length)
    val idEnd = afterName.indexOf(')')
    if (idEnd < 0) return null
    val ruleId = afterName.substring(0, idEnd)
    val remainder = afterName.substring(idEnd + 1).trimStart()
    val emDash = remainder.indexOf(" — ")
    if (emDash < 0) return null
    val action = remainder.substring(0, emDash)
    val detailWithDot = remainder.substring(emDash + 3)
    if (!detailWithDot.endsWith('.')) return null
    return ParsedRuleExplanation(
        name = name,
        ruleId = ruleId,
        action = action,
        detail = detailWithDot.removeSuffix("."),
    )
}

internal fun matchedRuleLabel(result: MatchResult): String =
    when {
        result.ruleId == null -> "none (default behavior)"
        else -> parseRuleExplanation(result.explanation)?.name ?: result.ruleId
    }

internal fun previewDiagnosticExplanation(result: MatchResult): String {
    val parsed = parseRuleExplanation(result.explanation) ?: return result.explanation
    return "${parsed.action} — ${parsed.detail}."
}

/**
 * Manual "test a number" section: routes [testInput] through
 * [CallGuardViewModel.onPreviewTested] (never a hand-rolled matcher check
 * here) and renders whatever [MatchResult] comes back. This composable is the
 * one place [MatchResult.action], [MatchResult.ruleId], and
 * [MatchResult.explanation] are shown.
 */
@Composable
fun RulePreviewScreen(
    testInput: String,
    onTestInputChanged: (String) -> Unit,
    onTestRequested: (String) -> Unit,
    result: MatchResult?,
    error: String?,
    notice: String? = null,
    stale: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text("Test a number", style = MaterialTheme.typography.titleMedium)
        Text(
            "See what this rule would do for a real number, without saving anything.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = testInput,
                onValueChange = onTestInputChanged,
                label = { Text("Phone number to test") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag(CallGuardTestTags.PREVIEW_INPUT_FIELD),
            )
            Button(
                onClick = { onTestRequested(testInput) },
                modifier = Modifier.testTag(CallGuardTestTags.PREVIEW_TEST_BUTTON),
            ) {
                Text("Test")
            }
        }
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp).testTag(CallGuardTestTags.PREVIEW_ERROR),
            )
        }
        if (notice != null) {
            Text(
                text = notice,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 8.dp).testTag(CallGuardTestTags.PREVIEW_NOTICE),
            )
        }
        if (result != null) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                if (stale) {
                    Text(
                        "Preview is out of date; test again after changing the rule.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(CallGuardTestTags.PREVIEW_STALE),
                    )
                }
                Text(
                    text = "CallGuard would: ${result.action.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(CallGuardTestTags.PREVIEW_RESULT_ACTION),
                )
                Text(
                    text = "Matched rule: ${matchedRuleLabel(result)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(CallGuardTestTags.PREVIEW_RESULT_RULE_ID),
                )
                Text(
                    text = previewDiagnosticExplanation(result),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(CallGuardTestTags.PREVIEW_RESULT_EXPLANATION),
                )
            }
        }
    }
}
