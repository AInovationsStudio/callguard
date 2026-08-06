package studio.ainovations.callguard.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import studio.ainovations.callguard.domain.MatchResult
import studio.ainovations.callguard.domain.RuleAction

@RunWith(AndroidJUnit4::class)
class RuleWizardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun guidedFlowAcceptsStartsWithBlockAndTestsNumber() {
        val inputEvents = mutableListOf<Pair<WizardField, String>>()
        var selectedMatcher: WizardMatcherType? = null
        var selectedAction: RuleAction? = null
        val testedInputs = mutableListOf<String>()
        var saveCount = 0

        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    WizardState(
                        canSave = true,
                        positiveExample = WizardExample("15718884444"),
                        negativeExample = WizardExample("25718884444"),
                    ),
                )
            }
            RuleWizardScreen(
                state = state,
                availableRegions = listOf("US"),
                onInputChanged = { field, value ->
                    inputEvents += field to value
                    state = when (field) {
                        WizardField.RAW_VALUE -> state.copy(rawValue = value)
                        WizardField.PREVIEW_INPUT -> state.copy(previewInput = value)
                        else -> state
                    }
                },
                onMatcherSelected = {
                    selectedMatcher = it
                    state = state.copy(matcherType = it)
                },
                onActionSelected = {
                    selectedAction = it
                    state = state.copy(action = it)
                },
                onPreviewTested = {
                    testedInputs += it
                    state = state.copy(previewInput = it)
                },
                onSave = { saveCount += 1 },
                onCancel = {},
            )
        }

        composeRule.onNodeWithTag(CallGuardTestTags.WIZARD_RAW_VALUE_FIELD)
            .performTextInput("1571888")
        composeRule.onNodeWithTag(CallGuardTestTags.MATCHER_TYPE_CHIP_PREFIX + "STARTS_WITH")
            .performClick()
        composeRule.onNodeWithTag(CallGuardTestTags.ACTION_CHIP_PREFIX + "BLOCK")
            .performClick()
        composeRule.onNodeWithTag(CallGuardTestTags.PREVIEW_INPUT_FIELD)
            .performTextInput("15718881234")
        composeRule.onNodeWithTag(CallGuardTestTags.PREVIEW_TEST_BUTTON).performClick()
        composeRule.onNodeWithTag(CallGuardTestTags.PREVIEW_INPUT_FIELD).performTextClearance()
        composeRule.onNodeWithTag(CallGuardTestTags.PREVIEW_INPUT_FIELD)
            .performTextInput("25718881234")
        composeRule.onNodeWithTag(CallGuardTestTags.PREVIEW_TEST_BUTTON).performClick()
        composeRule.onNodeWithTag(CallGuardTestTags.WIZARD_SAVE_BUTTON)
            .performScrollTo()
            .performClick()

        assert(selectedMatcher == WizardMatcherType.STARTS_WITH)
        assert(selectedAction == RuleAction.BLOCK)
        assert(inputEvents.any { it == WizardField.RAW_VALUE to "1571888" })
        assert(testedInputs == listOf("15718881234", "25718881234"))
        assert(saveCount == 1)
        composeRule.onNodeWithTag(
            CallGuardTestTags.WIZARD_POSITIVE_EXAMPLE,
            useUnmergedTree = true,
        ).assertTextEquals("Will match, e.g. 15718884444")
        composeRule.onNodeWithTag(
            CallGuardTestTags.WIZARD_NEGATIVE_EXAMPLE,
            useUnmergedTree = true,
        ).assertTextEquals("Will NOT match, e.g. 25718884444")
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

        composeRule.onNodeWithTag(CallGuardTestTags.PREVIEW_RESULT_ACTION, useUnmergedTree = true)
            .assertTextEquals("CallGuard would: Block")
        composeRule.onNodeWithTag(CallGuardTestTags.PREVIEW_RESULT_RULE_ID, useUnmergedTree = true)
            .assertTextEquals("Matched rule: rule-1")
        composeRule.onNodeWithTag(
            CallGuardTestTags.PREVIEW_RESULT_EXPLANATION,
            useUnmergedTree = true,
        )
            .assertTextEquals("Starts with 1571888, so this call will be blocked.")
    }

    @Test
    fun editStateKeepsTitleAndPriorityVisible() {
        composeRule.setContent {
            RuleWizardScreen(
                state = WizardState(
                    editingRuleId = "rule-1",
                    priority = 12,
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

        composeRule.onNodeWithText("Edit rule").assertIsDisplayed()
        composeRule.onNodeWithTag(CallGuardTestTags.WIZARD_PRIORITY_FIELD).assertIsDisplayed()
    }

    @Test
    fun contactsPreviewExplainsThatRealContactsAreNotSimulated() {
        composeRule.setContent {
            RuleWizardScreen(
                state = WizardState(
                    matcherType = WizardMatcherType.CONTACTS,
                    previewNotice = "Contacts are not simulated here.",
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

        composeRule.onNodeWithTag(CallGuardTestTags.PREVIEW_NOTICE)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun silenceActionIsHiddenWhenPlatformDoesNotSupportIt() {
        composeRule.setContent {
            RuleWizardScreen(
                state = WizardState(),
                availableRegions = emptyList(),
                onInputChanged = { _, _ -> },
                onMatcherSelected = {},
                onActionSelected = {},
                onPreviewTested = {},
                onSave = {},
                onCancel = {},
                silenceSupported = false,
            )
        }

        composeRule.onAllNodesWithTag(CallGuardTestTags.ACTION_CHIP_PREFIX + "SILENCE")
            .assertCountEquals(0)
    }

    @Test
    fun wizardBackHandlerRoutesToCancelCallback() {
        var cancelCount = 0
        composeRule.setContent {
            RuleWizardScreen(
                state = WizardState(),
                availableRegions = emptyList(),
                onInputChanged = { _, _ -> },
                onMatcherSelected = {},
                onActionSelected = {},
                onPreviewTested = {},
                onSave = {},
                onCancel = { cancelCount += 1 },
            )
        }

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        check(cancelCount == 1)
    }

    @Test
    fun contactsMatcherUsesConsistentRepairCopy() {
        composeRule.setContent {
            RuleWizardScreen(
                state = WizardState(matcherType = WizardMatcherType.CONTACTS),
                availableRegions = emptyList(),
                onInputChanged = { _, _ -> },
                onMatcherSelected = {},
                onActionSelected = {},
                onPreviewTested = {},
                onSave = {},
                onCancel = {},
                contactsPermissionGranted = false,
            )
        }

        composeRule.onNodeWithText(CallGuardUiCopy.CONTACTS_REPAIR_CTA).assertIsDisplayed()
    }
}
