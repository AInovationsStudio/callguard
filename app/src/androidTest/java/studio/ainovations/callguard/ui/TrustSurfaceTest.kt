package studio.ainovations.callguard.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import studio.ainovations.callguard.ui.theme.AINOVATIONS_FOOTER_TEST_TAG
import studio.ainovations.callguard.ui.theme.AINOVATIONS_WORDMARK_TEST_TAG

@RunWith(AndroidJUnit4::class)
class TrustSurfaceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun inactiveScreeningRoleUsesConsistentActivationCopy() {
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
        composeRule.onNodeWithText(CallGuardUiCopy.SCREENING_ROLE_CTA).assertIsDisplayed()
        composeRule.onNodeWithText("CallGuard is not protecting calls yet.").assertIsDisplayed()
        composeRule.onNodeWithText(CallGuardUiCopy.SCREENING_ROLE_INACTIVE_BANNER).assertIsDisplayed()
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
        composeRule.onNodeWithContentDescription("Add rule").assertIsDisplayed()
        composeRule.onAllNodesWithTag(AINOVATIONS_WORDMARK_TEST_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AINOVATIONS_FOOTER_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun settingsUsesCenteredTitleBrandedFooterAndAboutCard() {
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
        composeRule.onNodeWithTag(CallGuardTestTags.SETTINGS_ABOUT_CARD)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(CallGuardTestTags.SETTINGS_ABOUT_VERSION)
            .performScrollTo()
            .assertTextEquals("Version 0.1.0")
        composeRule.onNodeWithText("Licensed under Apache-2.0.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(CallGuardUiCopy.REPO_URL)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "CallGuard screens calls locally on your device. No account, analytics, or " +
                "network access is required for screening.",
        )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun contactsWarningIsHiddenWhenPermissionGranted() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(contactsPermissionGranted = true, contactMatchingEnabled = true),
                availableRegions = listOf("US"),
                onDefaultRegionChanged = {},
                onUnknownNumberActionChanged = {},
                onContactMatchingToggled = {},
                onRepairContactsPermission = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag(CallGuardTestTags.SETTINGS_CONTACTS_PERMISSION_WARNING)
            .assertDoesNotExist()
        composeRule.onNodeWithText(CallGuardUiCopy.CONTACTS_GRANTED_STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(CallGuardTestTags.SETTINGS_CONTACT_MATCHING_SWITCH)
            .assertIsDisplayed()
    }

    @Test
    fun contactsWarningUsesCallGuardFallbackContractWhenPermissionMissing() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(contactsPermissionGranted = false),
                availableRegions = listOf("US"),
                onDefaultRegionChanged = {},
                onUnknownNumberActionChanged = {},
                onContactMatchingToggled = {},
                onRepairContactsPermission = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag(CallGuardTestTags.SETTINGS_CONTACTS_PERMISSION_WARNING)
            .assertTextEquals(CallGuardUiCopy.CONTACTS_RULES_DISABLED)
        composeRule.onNodeWithText(CallGuardUiCopy.CONTACTS_REPAIR_CTA).assertIsDisplayed()
    }

    @Test
    fun settingsScreeningRoleUsesConsistentActivationCopy() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(screeningRoleStatus = ScreeningRoleStatus.NotActive),
                availableRegions = emptyList(),
                onDefaultRegionChanged = {},
                onUnknownNumberActionChanged = {},
                onContactMatchingToggled = {},
                onRepairContactsPermission = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText(CallGuardUiCopy.SCREENING_ROLE_CTA).assertIsDisplayed()
    }

    @Test
    fun settingsHidesSilenceWhenPlatformDoesNotSupportIt() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                availableRegions = emptyList(),
                onDefaultRegionChanged = {},
                onUnknownNumberActionChanged = {},
                onContactMatchingToggled = {},
                onRepairContactsPermission = {},
                onBack = {},
                silenceSupported = false,
            )
        }

        composeRule.onAllNodesWithTag(CallGuardTestTags.SETTINGS_UNKNOWN_ACTION_CHIP_PREFIX + "SILENCE")
            .assertCountEquals(0)
        composeRule.onNodeWithTag(CallGuardTestTags.SETTINGS_UNKNOWN_ACTION_CHIP_PREFIX + "ALLOW")
            .assertIsDisplayed()
    }

    @Test
    fun settingsBackHandlerRoutesToCallback() {
        var backCount = 0
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(),
                availableRegions = emptyList(),
                onDefaultRegionChanged = {},
                onUnknownNumberActionChanged = {},
                onContactMatchingToggled = {},
                onRepairContactsPermission = {},
                onBack = { backCount += 1 },
            )
        }

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        check(backCount == 1)
    }

    @Test
    fun ruleToggleSemanticsUseStateNeutralDescription() {
        composeRule.setContent {
            RuleListScreen(
                items = listOf(
                    RuleListItem(
                        id = "rule-1",
                        name = "Suspicious callers",
                        actionLabel = "Block",
                        matcherDescription = "starts with 157",
                        enabled = false,
                        priority = 0,
                        requiresContactsPermission = false,
                    ),
                ),
                onAddRule = {},
                onEditRule = {},
                onDeleteRule = {},
                onToggleRule = { _, _ -> },
                onOpenSettings = {},
                screeningRoleStatus = ScreeningRoleStatus.Active,
            )
        }

        val toggle = composeRule.onNodeWithTag(CallGuardTestTags.RULE_TOGGLE_PREFIX + "rule-1")
        toggle.assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Suspicious callers rule").assertIsDisplayed()
        val semantics = toggle.fetchSemanticsNode().config
        check(semantics.getOrNull(SemanticsProperties.StateDescription) == "Off")
    }

    @Test
    fun ruleToggleSemanticsAnnounceOnWhenEnabled() {
        composeRule.setContent {
            RuleListScreen(
                items = listOf(
                    RuleListItem(
                        id = "rule-2",
                        name = "Trusted contacts",
                        actionLabel = "Allow",
                        matcherDescription = "contacts",
                        enabled = true,
                        priority = 0,
                        requiresContactsPermission = true,
                    ),
                ),
                onAddRule = {},
                onEditRule = {},
                onDeleteRule = {},
                onToggleRule = { _, _ -> },
                onOpenSettings = {},
                screeningRoleStatus = ScreeningRoleStatus.Active,
            )
        }

        val toggle = composeRule.onNodeWithTag(CallGuardTestTags.RULE_TOGGLE_PREFIX + "rule-2")
        composeRule.onNodeWithContentDescription("Trusted contacts rule").assertIsDisplayed()
        val semantics = toggle.fetchSemanticsNode().config
        check(semantics.getOrNull(SemanticsProperties.StateDescription) == "On")
    }

    @Test
    fun contactMatchingSwitchSemanticsReflectDisabledState() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(contactsPermissionGranted = true, contactMatchingEnabled = false),
                availableRegions = listOf("US"),
                onDefaultRegionChanged = {},
                onUnknownNumberActionChanged = {},
                onContactMatchingToggled = {},
                onRepairContactsPermission = {},
                onBack = {},
            )
        }

        val toggle = composeRule.onNodeWithTag(CallGuardTestTags.SETTINGS_CONTACT_MATCHING_SWITCH)
        toggle.assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Match rules against your contacts").assertIsDisplayed()
        val semantics = toggle.fetchSemanticsNode().config
        check(semantics.getOrNull(SemanticsProperties.StateDescription) == "Off")
    }

    @Test
    fun contactMatchingSwitchSemanticsReflectEnabledState() {
        composeRule.setContent {
            SettingsScreen(
                state = SettingsUiState(contactsPermissionGranted = true, contactMatchingEnabled = true),
                availableRegions = listOf("US"),
                onDefaultRegionChanged = {},
                onUnknownNumberActionChanged = {},
                onContactMatchingToggled = {},
                onRepairContactsPermission = {},
                onBack = {},
            )
        }

        val toggle = composeRule.onNodeWithTag(CallGuardTestTags.SETTINGS_CONTACT_MATCHING_SWITCH)
        composeRule.onNodeWithContentDescription("Match rules against your contacts").assertIsDisplayed()
        val semantics = toggle.fetchSemanticsNode().config
        check(semantics.getOrNull(SemanticsProperties.StateDescription) == "On")
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
