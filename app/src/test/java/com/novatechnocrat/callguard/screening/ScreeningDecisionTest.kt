package studio.ainovations.callguard.screening

import studio.ainovations.callguard.data.CallGuardPreferences
import studio.ainovations.callguard.data.RuleSnapshot
import studio.ainovations.callguard.domain.BlockingRule
import studio.ainovations.callguard.domain.RuleAction
import studio.ainovations.callguard.domain.RuleMatcher
import studio.ainovations.callguard.phone.PhoneNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreeningDecisionTest {
    private val resolver = ScreeningDecisionResolver(
        normalizer = PhoneNormalizer(deviceRegion = { "US" }),
    )

    @Test
    fun matchingBlockRuleReturnsBlock() {
        val result = resolve(
            rules = listOf(rule("block-prefix", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888"))),
        )

        assertEquals(RuleAction.BLOCK, result.action)
        assertEquals("block-prefix", result.ruleId)
    }

    @Test
    fun matchingSilenceRuleReturnsSilence() {
        val result = resolve(
            rules = listOf(rule("silence-prefix", RuleAction.SILENCE, RuleMatcher.StartsWith("1571888"))),
        )

        assertEquals(RuleAction.SILENCE, result.action)
        assertEquals("silence-prefix", result.ruleId)
    }

    @Test
    fun matchingAllowRuleReturnsAllow() {
        val result = resolve(
            rules = listOf(rule("allow-exact", RuleAction.ALLOW, RuleMatcher.Exact("15718881234"))),
        )

        assertEquals(RuleAction.ALLOW, result.action)
        assertEquals("allow-exact", result.ruleId)
    }

    @Test
    fun noMatchingRuleUsesDefaultAllow() {
        val result = resolve(
            rules = listOf(rule("block-prefix", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888"))),
            rawNumber = "18005551234",
        )

        assertEquals(RuleAction.ALLOW, result.action)
        assertNull(result.ruleId)
        assertTrue(result.explanation.contains("default", ignoreCase = true))
    }

    @Test
    fun unknownNumberUsesConfiguredSafeDefault() {
        val result = resolver.resolve(
            snapshot = RuleSnapshot.EMPTY,
            rawNumber = null,
            region = "US",
            preferences = CallGuardPreferences(
                defaultRegion = "US",
                unknownNumberAction = RuleAction.BLOCK,
                contactMatchingEnabled = false,
            ),
            contacts = emptySet(),
        )

        assertEquals(RuleAction.BLOCK, result.action)
        assertNull(result.ruleId)
        assertTrue(result.explanation.contains("unknown", ignoreCase = true))
    }

    @Test
    fun higherPriorityAllowWinsOverBroadBlock() {
        val result = resolve(
            rules = listOf(
                rule("broad-block", RuleAction.BLOCK, RuleMatcher.Regex(".*"), priority = 0),
                rule("exact-allow", RuleAction.ALLOW, RuleMatcher.Exact("15718881234"), priority = 10),
            ),
        )

        assertEquals(RuleAction.ALLOW, result.action)
        assertEquals("exact-allow", result.ruleId)
    }

    @Test
    fun contactRuleUsesCanonicalContactSet() {
        val result = resolver.resolve(
            snapshot = RuleSnapshot.compile(
                listOf(rule("contact-allow", RuleAction.ALLOW, RuleMatcher.Contacts)),
            ),
            rawNumber = "15718881234",
            region = "US",
            preferences = CallGuardPreferences(
                defaultRegion = "US",
                unknownNumberAction = RuleAction.ALLOW,
                contactMatchingEnabled = true,
            ),
            contacts = setOf("15718881234"),
        )

        assertEquals(RuleAction.ALLOW, result.action)
        assertEquals("contact-allow", result.ruleId)
    }

    private fun resolve(
        rules: List<BlockingRule>,
        rawNumber: String = "15718881234",
    ) = resolver.resolve(
        snapshot = RuleSnapshot.compile(rules),
        rawNumber = rawNumber,
        region = "US",
        preferences = CallGuardPreferences(
            defaultRegion = "US",
            unknownNumberAction = RuleAction.ALLOW,
            contactMatchingEnabled = false,
        ),
        contacts = emptySet(),
    )

    private fun rule(
        id: String,
        action: RuleAction,
        matcher: RuleMatcher,
        priority: Int = 0,
    ) = BlockingRule(
        id = id,
        name = id,
        enabled = true,
        action = action,
        matcher = matcher,
        priority = priority,
    )
}
