package studio.ainovations.callguard.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Android-independent rule engine. Runs on the JVM; no
 * Android types are referenced here or in the domain package.
 */
class RuleCompilerTest {

    private val compiler = RuleCompiler

    private fun rule(
        id: String,
        action: RuleAction,
        matcher: RuleMatcher,
        priority: Int = 0,
        enabled: Boolean = true,
        name: String = id,
    ): BlockingRule = BlockingRule(id, name, enabled, action, matcher, priority)

    @Test
    fun exactAllowMatchesExactNumber() {
        val rules = listOf(
            rule("allow-exact", RuleAction.ALLOW, RuleMatcher.Exact("15718881234")),
        )
        val result = compiler.evaluate("15718881234", rules, emptySet())
        assertEquals(RuleAction.ALLOW, result.action)
        assertEquals("allow-exact", result.ruleId)
        assertTrue(result.explanation.contains("allow-exact"))
    }

    @Test
    fun prefixBlockMatchesNumberWithPrefix() {
        val rules = listOf(
            rule("block-prefix", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888")),
        )
        val result = compiler.evaluate("15718881234", rules, emptySet())
        assertEquals(RuleAction.BLOCK, result.action)
        assertEquals("block-prefix", result.ruleId)
    }

    @Test
    fun suffixBlockMatchesNumberWithSuffix() {
        val rules = listOf(
            rule("block-suffix", RuleAction.BLOCK, RuleMatcher.EndsWith("1234")),
        )
        val result = compiler.evaluate("15718881234", rules, emptySet())
        assertEquals(RuleAction.BLOCK, result.action)
        assertEquals("block-suffix", result.ruleId)
    }

    @Test
    fun containsBlockMatchesSubstring() {
        val rules = listOf(
            rule("block-contains", RuleAction.BLOCK, RuleMatcher.Contains("1888")),
        )
        val result = compiler.evaluate("15718881234", rules, emptySet())
        assertEquals(RuleAction.BLOCK, result.action)
        assertEquals("block-contains", result.ruleId)
    }

    @Test
    fun inclusiveRangeMatchesBoundariesAndInterior() {
        val rules = listOf(
            rule("block-range", RuleAction.BLOCK, RuleMatcher.Range("15718880000", "15718889999")),
        )
        assertEquals(RuleAction.BLOCK, compiler.evaluate("15718880000", rules, emptySet()).action)
        assertEquals(RuleAction.BLOCK, compiler.evaluate("15718889999", rules, emptySet()).action)
        assertEquals(RuleAction.BLOCK, compiler.evaluate("15718881234", rules, emptySet()).action)
        assertEquals(RuleAction.ALLOW, compiler.evaluate("15718870000", rules, emptySet()).action)
    }

    @Test
    fun specificNumberAllowMatchesMember() {
        val rules = listOf(
            rule("allow-specific", RuleAction.ALLOW, RuleMatcher.SpecificNumbers(setOf("15718881234", "18005551234"))),
        )
        assertEquals(RuleAction.ALLOW, compiler.evaluate("15718881234", rules, emptySet()).action)
        assertEquals(RuleAction.ALLOW, compiler.evaluate("18005551234", rules, emptySet()).action)
        assertEquals(RuleAction.ALLOW, compiler.evaluate("19999999999", rules, emptySet()).action)
        assertEquals("allow-specific", compiler.evaluate("15718881234", rules, emptySet()).ruleId)
    }

    @Test
    fun contactsMatcherUsesProvidedContactSet() {
        val rules = listOf(
            rule("allow-contacts", RuleAction.ALLOW, RuleMatcher.Contacts),
        )
        assertEquals(RuleAction.ALLOW, compiler.evaluate("15718881234", rules, setOf("15718881234")).action)
        assertEquals(RuleAction.ALLOW, compiler.evaluate("19999999999", rules, emptySet()).action)
    }

    @Test
    fun disabledRulesAreSkipped() {
        val rules = listOf(
            rule("disabled-block", RuleAction.BLOCK, RuleMatcher.Exact("15718881234"), enabled = false),
        )
        val result = compiler.evaluate("15718881234", rules, emptySet())
        assertEquals(RuleAction.ALLOW, result.action)
        assertNull(result.ruleId)
    }

    @Test
    fun defaultAllowWhenNoRuleMatches() {
        val rules = listOf(
            rule("block-prefix", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888")),
        )
        val result = compiler.evaluate("18005551234", rules, emptySet())
        assertEquals(RuleAction.ALLOW, result.action)
        assertNull(result.ruleId)
        assertTrue(result.explanation.lowercase().contains("default"))
    }

    @Test
    fun explicitPriorityBeatsLowerPriority() {
        val rules = listOf(
            rule("low-block", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888"), priority = 1),
            rule("high-allow", RuleAction.ALLOW, RuleMatcher.Exact("15718881234"), priority = 10),
        )
        val result = compiler.evaluate("15718881234", rules, emptySet())
        assertEquals(RuleAction.ALLOW, result.action)
        assertEquals("high-allow", result.ruleId)
    }

    @Test
    fun broadBlockDoesNotDefeatHigherPriorityExactAllow() {
        // The must-fail guard: a broad block that also matches must not win
        // over an exact allow with higher explicit priority.
        val rules = listOf(
            rule("broad-block", RuleAction.BLOCK, RuleMatcher.Regex(".*"), priority = 0),
            rule("exact-allow", RuleAction.ALLOW, RuleMatcher.Exact("15718881234"), priority = 5),
        )
        val result = compiler.evaluate("15718881234", rules, emptySet())
        assertEquals(RuleAction.ALLOW, result.action)
        assertEquals("exact-allow", result.ruleId)
    }

    @Test
    fun specificityBreaksPriorityTie() {
        // Equal priority: exact beats prefix.
        val rules = listOf(
            rule("prefix-block", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888"), priority = 5),
            rule("exact-allow", RuleAction.ALLOW, RuleMatcher.Exact("15718881234"), priority = 5),
        )
        val result = compiler.evaluate("15718881234", rules, emptySet())
        assertEquals(RuleAction.ALLOW, result.action)
        assertEquals("exact-allow", result.ruleId)
    }

    @Test
    fun stableOrderPreservedForEqualPriorityAndSpecificity() {
        // Two prefix rules, same priority: the first declared wins.
        val rules = listOf(
            rule("first-block", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888"), priority = 0),
            rule("second-allow", RuleAction.ALLOW, RuleMatcher.StartsWith("1571888"), priority = 0),
        )
        val result = compiler.evaluate("15718881234", rules, emptySet())
        assertEquals(RuleAction.BLOCK, result.action)
        assertEquals("first-block", result.ruleId)
    }

    @Test
    fun explanationNamesWinningRuleAndMatcher() {
        val rules = listOf(
            rule("block-prefix", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888"), name = "Spam prefix"),
        )
        val result = compiler.evaluate("15718881234", rules, emptySet())
        assertTrue(result.explanation.contains("block-prefix"))
        assertTrue(result.explanation.lowercase().contains("block"))
    }

    @Test
    fun regexMatcherMatchesCanonicalDigits() {
        val rules = listOf(
            rule("regex-block", RuleAction.BLOCK, RuleMatcher.Regex("1571\\d{6}")),
        )
        assertEquals(RuleAction.BLOCK, compiler.evaluate("1571888123", rules, emptySet()).action)
        assertEquals(RuleAction.ALLOW, compiler.evaluate("1572888123", rules, emptySet()).action)
    }

    @Test
    fun regexMatcherRejectsInvalidExpression() {
        val rules = listOf(
            rule("regex-bad", RuleAction.BLOCK, RuleMatcher.Regex("1571(")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            compiler.evaluate("15718881234", rules, emptySet())
        }
    }

    @Test
    fun regexMatcherRejectsOverLongPattern() {
        val long = "1" + "0".repeat(RuleCompiler.MAX_REGEX_LENGTH)
        val rules = listOf(
            rule("regex-long", RuleAction.BLOCK, RuleMatcher.Regex(long)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            compiler.evaluate("15718881234", rules, emptySet())
        }
    }

    @Test
    fun regexMatcherRejectsNestedRepetition() {
        val rules = listOf(
            rule("regex-nested", RuleAction.BLOCK, RuleMatcher.Regex("(\\d+)+")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            compiler.evaluate("15718881234", rules, emptySet())
        }
    }

    @Test
    fun emptyMatcherValueIsRejectedBeforeEvaluation() {
        val rules = listOf(
            rule("empty-prefix", RuleAction.BLOCK, RuleMatcher.StartsWith("")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            compiler.evaluate("15718881234", rules, emptySet())
        }
    }

    @Test
    fun nonDigitCanonicalValueIsRejected() {
        val rules = listOf(
            rule("alpha-prefix", RuleAction.BLOCK, RuleMatcher.StartsWith("+1571888")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            compiler.evaluate("15718881234", rules, emptySet())
        }
    }

    @Test
    fun emptyInputNumberDefaultsToAllow() {
        val rules = listOf(
            rule("block-prefix", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888")),
        )
        val result = compiler.evaluate("", rules, emptySet())
        assertEquals(RuleAction.ALLOW, result.action)
        assertNull(result.ruleId)
    }

    // --- Regression tests for disabled-rule compilation ---

    @Test
    fun disabledInvalidRuleDoesNotBreakEvaluation() {
        // A disabled rule carrying a malformed matcher (nested-repetition
        // regex) must be skipped before compilation, so a valid enabled rule
        // still evaluates and no exception escapes.
        val rules = listOf(
            rule("disabled-bad-regex", RuleAction.BLOCK, RuleMatcher.Regex("(\\d+)+"), enabled = false),
            rule("enabled-exact-allow", RuleAction.ALLOW, RuleMatcher.Exact("15718881234")),
        )
        val result = compiler.evaluate("15718881234", rules, emptySet())
        assertEquals(RuleAction.ALLOW, result.action)
        assertEquals("enabled-exact-allow", result.ruleId)
    }

    @Test
    fun disabledEmptyMatcherDoesNotBreakEvaluation() {
        val rules = listOf(
            rule("disabled-empty-prefix", RuleAction.BLOCK, RuleMatcher.StartsWith(""), enabled = false),
            rule("enabled-prefix-block", RuleAction.BLOCK, RuleMatcher.StartsWith("1571888")),
        )
        val result = compiler.evaluate("15718881234", rules, emptySet())
        assertEquals(RuleAction.BLOCK, result.action)
        assertEquals("enabled-prefix-block", result.ruleId)
    }

    @Test
    fun silenceActionReturnedWhenSilenceRuleMatches() {
        val rules = listOf(
            rule("silence-exact", RuleAction.SILENCE, RuleMatcher.Exact("15718881234")),
        )
        val result = compiler.evaluate("15718881234", rules, emptySet())
        assertEquals(RuleAction.SILENCE, result.action)
        assertEquals("silence-exact", result.ruleId)
        assertTrue(result.explanation.lowercase().contains("silence"))
    }

    @Test
    fun emptyRangeBoundIsRejected() {
        val rules = listOf(
            rule("empty-range-start", RuleAction.BLOCK, RuleMatcher.Range("", "15718889999")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            compiler.evaluate("15718881234", rules, emptySet())
        }
    }

    @Test
    fun emptyRangeEndIsRejected() {
        val rules = listOf(
            rule("empty-range-end", RuleAction.BLOCK, RuleMatcher.Range("15718880000", "")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            compiler.evaluate("15718881234", rules, emptySet())
        }
    }

    @Test
    fun rangeStartGreaterThanEndIsRejected() {
        val rules = listOf(
            rule("reversed-range", RuleAction.BLOCK, RuleMatcher.Range("99999", "10000")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            compiler.evaluate("15718881234", rules, emptySet())
        }
    }

    @Test
    fun regexMatcherRejectsRedundantNestedRepetition() {
        // Redundant outer grouping must not bypass the ReDoS guard.
        val rules = listOf(
            rule("regex-redundant-nested", RuleAction.BLOCK, RuleMatcher.Regex("((\\d+))+")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            compiler.evaluate("15718881234", rules, emptySet())
        }
    }

    @Test
    fun regexMatcherRejectsNestedRepetitionWithStarOuter() {
        val rules = listOf(
            rule("regex-nested-star", RuleAction.BLOCK, RuleMatcher.Regex("(\\d+)*")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            compiler.evaluate("15718881234", rules, emptySet())
        }
    }

    @Test
    fun regexAllowsQuantifiedGroupNotItselfQuantified() {
        // A group that contains a quantifier but is NOT itself quantified is
        // a normal, non-pathological pattern and must be accepted.
        val rules = listOf(
            rule("regex-ok", RuleAction.BLOCK, RuleMatcher.Regex("1571\\d+")),
        )
        assertEquals(RuleAction.BLOCK, compiler.evaluate("1571888", rules, emptySet()).action)
        assertEquals(RuleAction.ALLOW, compiler.evaluate("1572", rules, emptySet()).action)
    }
}
