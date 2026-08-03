package studio.ainovations.callguard.data

import studio.ainovations.callguard.domain.BlockingRule
import studio.ainovations.callguard.domain.MatchResult
import studio.ainovations.callguard.domain.RuleCompiler
import studio.ainovations.callguard.domain.RuleMatcher

/**
 * An immutable, fully compiled rule set published atomically by
 * [RuleRepository]. [RuleCompiler.compile] validates and compiles every
 * enabled rule's matcher — parsing regex into a [java.util.regex.Pattern]
 * and range bounds into [java.math.BigInteger] — exactly once, when the
 * snapshot is built; [evaluate] performs no compilation, so it is safe to
 * call repeatedly on the screening hot path without recompiling raw
 * matchers per call.
 *
 * A [RuleSnapshot] instance never changes after construction. A caller that
 * holds a reference to one snapshot keeps seeing that snapshot's rules even
 * after [RuleRepository.replaceRules] publishes a newer one.
 */
class RuleSnapshot private constructor(
    private val compiled: RuleCompiler.CompiledRuleSet,
    val hasContactRules: Boolean,
) {

    /**
     * Evaluate [numberDigits] (canonical digit-only form) against this
     * snapshot's compiled rules, resolving contact-matcher rules against
     * [contacts]. Never throws: validation already happened in [compile].
     */
    fun evaluate(numberDigits: String, contacts: Set<String>): MatchResult =
        compiled.evaluate(numberDigits, contacts)

    companion object {

        /** An empty snapshot: every number defaults to allow. */
        val EMPTY: RuleSnapshot = compile(emptyList())

        /**
         * Compile [rules] into an immutable snapshot.
         *
         * @throws IllegalArgumentException if any enabled rule carries an
         *   invalid matcher (empty/non-digit canonical value, or an invalid,
         *   over-long, or nested-repetition regex) — mirrors
         *   [RuleCompiler.compile]'s validation.
         */
        fun compile(rules: List<BlockingRule>): RuleSnapshot = RuleSnapshot(
            compiled = RuleCompiler.compile(rules),
            hasContactRules = rules.any { it.enabled && it.matcher == RuleMatcher.Contacts },
        )
    }
}
