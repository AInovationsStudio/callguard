package studio.ainovations.callguard.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import studio.ainovations.callguard.ui.theme.AINOVATIONS_FOOTER_TEST_TAG
import studio.ainovations.callguard.ui.theme.AINOVATIONS_WORDMARK_TEST_TAG

@RunWith(AndroidJUnit4::class)
class TrustSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inactiveScreeningRoleIsVisibleOnRuleList() {
        composeRule.setContent {
            RuleListScreen(
                items = emptyList(),
                onAddRule = {},
                onEditRule = {},
                onDeleteRule = {},
                onToggleRule = { _, _ -> },
                onOpenSettings = {},
                screeningRoleStatus = ScreeningRoleStatus.NotActive,
                onRequestScreeningRole = {},
            )
        }

        composeRule.onNodeWithTag(CallGuardTestTags.RULE_LIST_SCREENING_STATUS)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Activate CallGuard").assertIsDisplayed()
    }

    @Test
    fun deletingRuleRequiresConfirmation() {
        var deletedId: String? = null
        composeRule.setContent {
            RuleListScreen(
                items = listOf(
                    RuleListItem(
                        id = "rule-1",
                        name = "Suspicious callers",
                        actionLabel = "Block",
                        matcherDescription = "starts with 157",
                        enabled = true,
                        priority = 0,
                        requiresContactsPermission = false,
                    ),
                ),
                onAddRule = {},
                onEditRule = {},
                onDeleteRule = { deletedId = it },
                onToggleRule = { _, _ -> },
                onOpenSettings = {},
                screeningRoleStatus = ScreeningRoleStatus.Active,
            )
        }

        composeRule.onNodeWithTag(CallGuardTestTags.RULE_DELETE_PREFIX + "rule-1").performClick()
        composeRule.onNodeWithText("Delete rule?").assertIsDisplayed()
        check(deletedId == null)
        composeRule.onNodeWithTag(CallGuardTestTags.RULE_DELETE_CONFIRM_BUTTON).performClick()
        check(deletedId == "rule-1")
    }

    @Test
    fun ruleListKeepsFabWithoutARepeatedBrandBadge() {
        composeRule.setContent {
            RuleListScreen(
                items = emptyList(),
                onAddRule = {},
                onEditRule = {},
                onDeleteRule = {},
                onToggleRule = { _, _ -> },
                onOpenSettings = {},
                screeningRoleStatus = ScreeningRoleStatus.Active,
            )
        }

        composeRule.onNodeWithTag(CallGuardTestTags.ADD_RULE_BUTTON).assertIsDisplayed()
        composeRule.onAllNodesWithTag(AINOVATIONS_WORDMARK_TEST_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AINOVATIONS_FOOTER_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun settingsUsesCenteredTitleAndBrandedFooter() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                availableRegions = listOf("US"),
                onDefaultRegionChanged = {},
                onUnknownNumberActionChanged = {},
                onContactMatchingToggled = {},
                onRepairContactsPermission = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithTag(AINOVATIONS_WORDMARK_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(AINOVATIONS_FOOTER_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(CallGuardTestTags.SETTINGS_CONTACTS_PERMISSION_WARNING)
            .assertIsDisplayed()
    }

    @Test
    fun wizardTitleUsesDedicatedCenteredTitleSurface() {
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
            )
        }

        composeRule.onNodeWithTag(CallGuardTestTags.WIZARD_TITLE)
            .assertIsDisplayed()
    }
}
