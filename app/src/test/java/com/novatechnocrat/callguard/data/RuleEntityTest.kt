package studio.ainovations.callguard.data

import studio.ainovations.callguard.domain.BlockingRule
import studio.ainovations.callguard.domain.RuleAction
import studio.ainovations.callguard.domain.RuleMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Unit tests for [RuleEntity]'s mapping functions and [RuleEntityConverters],
 * focused on the [RuleMatcher.SpecificNumbers] comma-delimited CSV encoding a
 * persistence layer review flagged as a latent round-trip risk: joining a set of
 * canonical digit strings with a comma is only collision-free because every
 * element is guaranteed to be a non-empty, digit-only string. A value
 * containing a comma would otherwise round-trip as a *different* set of
 * numbers (silent corruption, not a crash). These tests pin the invariant at
 * both persistence boundaries it crosses — [BlockingRule.toEntity] and
 * [RuleEntityConverters.fromStringSet] — independent of
 * [studio.ainovations.callguard.domain.RuleCompiler]'s upstream validation,
 * which only runs for enabled rules (see [RuleRepositoryTest] for the
 * disabled-rule persistence path this closes).
 */
class RuleEntityTest {

    private val converters = RuleEntityConverters()

    private fun specificNumbersRule(numbers: Set<String>, enabled: Boolean = true): BlockingRule = BlockingRule(
        id = "specific",
        name = "specific",
        enabled = enabled,
        action = RuleAction.ALLOW,
        matcher = RuleMatcher.SpecificNumbers(numbers),
        priority = 0,
    )

    @Test
    fun specificNumbersRoundTripsThroughEntityMapping() {
        val entity = specificNumbersRule(setOf("15718881234", "18005551234")).toEntity(position = 0)
        val roundTripped = (entity.toDomain().matcher as RuleMatcher.SpecificNumbers).numbers
        assertEquals(setOf("15718881234", "18005551234"), roundTripped)
    }

    @Test
    fun toEntityRejectsACommaInASpecificNumberBeforeItReachesTheDatabase() {
        // A comma inside a "number" would be indistinguishable, after encode
        // then decode, from two separate numbers split at that comma — the
        // exact silent-corruption shape the CSV encoding must never allow.
        // This must be caught even for a DISABLED rule: RuleCompiler skips
        // disabled rules entirely, but replaceRules persists them anyway.
        assertThrows(IllegalArgumentException::class.java) {
            specificNumbersRule(setOf("1571888,1234"), enabled = false).toEntity(position = 0)
        }
    }

    @Test
    fun toEntityRejectsAnEmptySpecificNumberElement() {
        assertThrows(IllegalArgumentException::class.java) {
            specificNumbersRule(setOf("")).toEntity(position = 0)
        }
    }

    @Test
    fun toEntityRejectsANonDigitSpecificNumberElement() {
        assertThrows(IllegalArgumentException::class.java) {
            specificNumbersRule(setOf("1571888x")).toEntity(position = 0)
        }
    }

    @Test
    fun fromStringSetRejectsACommaInAnElementEvenIfToEntityIsBypassed() {
        // Defense in depth: a caller that builds a RuleEntity directly,
        // skipping BlockingRule.toEntity, must still be unable to write an
        // ambiguous CSV value through the actual Room persistence boundary.
        assertThrows(IllegalArgumentException::class.java) {
            converters.fromStringSet(setOf("1571888,1234"))
        }
    }

    @Test
    fun emptySpecificNumbersSetRoundTripsToEmptySet() {
        assertEquals("", converters.fromStringSet(emptySet()))
        assertEquals(emptySet<String>(), converters.toStringSet(""))
    }

    @Test
    fun nullSpecificNumbersRoundTripsToNull() {
        assertEquals(null, converters.fromStringSet(null))
        assertEquals(null, converters.toStringSet(null))
    }
}
