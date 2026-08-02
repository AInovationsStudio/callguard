package studio.ainovations.callguard.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import studio.ainovations.callguard.domain.MatchResult
import studio.ainovations.callguard.domain.RuleAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuleWizardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun guidedFlowAcceptsStartsWithBlockAndTestsNumber() {
        val inputEvents = mutableListOf<Pair<WizardField, String>>()
        var selectedMatcher: WizardMatcherType? = null
        var selectedAction: RuleAction? = null
        var testedInput: String? = null

        composeRule.setContent {
            RuleWizardScreen(
                state = WizardState(
                    rawValue = "1571888",
                    positiveExample = WizardExample("15718884444"),
                    negativeExample = WizardExample("25718884444"),
                    previewInput = "",
                    canSave = true,
                ),
                availableRegions = listOf("US"),
                onInputChanged = { field, value -> inputEvents += field to value },
                onMatcherSelected = { selectedMatcher = it },
                onActionSelected = { selectedAction = it },
                onPreviewTested = { testedInput = it },
                onSave = {},
                onCancel = {},
            )
        }

        composeRule.onNodeWithTag(CallGuardTestTags.MATCHER_TYPE_CHIP_PREFIX + "STARTS_WITH")
            .performClick()
        composeRule.onNodeWithTag(CallGuardTestTags.ACTION_CHIP_PREFIX + "BLOCK")
            .performClick()
        composeRule.onNodeWithTag(CallGuardTestTags.PREVIEW_INPUT_FIELD)
            .performTextInput("15718881234")
        composeRule.onNodeWithTag(CallGuardTestTags.PREVIEW_TEST_BUTTON).performClick()

        assert(selectedMatcher == WizardMatcherType.STARTS_WITH)
        assert(selectedAction == RuleAction.BLOCK)
        assert(inputEvents.any { it.first == WizardField.PREVIEW_INPUT })
        assert(testedInput == "15718881234")
        composeRule.onNodeWithTag(CallGuardTestTags.WIZARD_POSITIVE_EXAMPLE).assertTextContains("Will match")
        composeRule.onNodeWithTag(CallGuardTestTags.WIZARD_NEGATIVE_EXAMPLE).assertTextContains("Will NOT match")
    }

    @Test
    fun broadRegexAndConflictWarningsAreVisible() {
        composeRule.setContent {
            RuleWizardScreen(
                state = WizardState(
                    matcherType = WizardMatcherType.ADVANCED_PATTERN,
                    rawValue = ".*",
                    broadRegexWarning = "This pattern matches almost any number.",
                    conflictWarnings = listOf("Conflicts with 'Existing block' for numbers like 1571888."),
                ),
                availableRegions = emptyList(),
                onInputChanged = { _, _ -> },
                onMatcherSelected = {},
                onActionSelected = {},
                onPreviewTested = {},
                onSave = {},
                onCancel = {},
            )
        }

        composeRule.onNodeWithTag(CallGuardTestTags.WIZARD_BROAD_REGEX_WARNING)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(CallGuardTestTags.WIZARD_CONFLICT_WARNING_PREFIX + "0")
            .assertIsDisplayed()
    }

    @Test
    fun previewRendersActionRuleIdAndExplanation() {
        composeRule.setContent {
            RulePreviewScreen(
                testInput = "15718881234",
                onTestInputChanged = {},
                onTestRequested = {},
                result = MatchResult(
                    action = RuleAction.BLOCK,
                    ruleId = "rule-1",
                    explanation = "Starts with 1571888, so this call will be blocked.",
                ),
                error = null,
            )
        }

        composeRule.onNodeWithTag(CallGuardTestTags.PREVIEW_RESULT_ACTION)
            .assertTextContains("BLOCK")
        composeRule.onNodeWithTag(CallGuardTestTags.PREVIEW_RESULT_RULE_ID)
            .assertTextContains("rule-1")
        composeRule.onNodeWithTag(CallGuardTestTags.PREVIEW_RESULT_EXPLANATION)
            .assertTextContains("blocked")
    }
}
