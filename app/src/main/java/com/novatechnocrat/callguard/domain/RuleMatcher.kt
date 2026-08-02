package studio.ainovations.callguard.domain

/**
 * A matcher against the canonical digit representation of an incoming number.
 *
 * Canonical values are digit-only strings (e.g. `"15718881234"`); display
 * formatting is retained separately for the UI and never reaches this layer.
 * Every concrete matcher rejects an empty canonical value before evaluation.
 */
sealed interface RuleMatcher {

    /**
     * Matches the full canonical digit string exactly.
     */
    data class Exact(val value: String) : RuleMatcher

    /**
     * Matches when the canonical digits start with [prefix].
     */
    data class StartsWith(val prefix: String) : RuleMatcher

    /**
     * Matches when the canonical digits end with [suffix].
     */
    data class EndsWith(val suffix: String) : RuleMatcher

    /**
     * Matches when the canonical digits contain [substring].
     */
    data class Contains(val substring: String) : RuleMatcher

    /**
     * Inclusive numeric range over canonical digit strings. A number matches
     * when its digit value falls within [startInclusive]..[endInclusive].
     */
    data class Range(val startInclusive: String, val endInclusive: String) : RuleMatcher

    /**
     * Matches when the canonical number is present in the caller-supplied
     * contact set. The domain layer never loads contacts itself; the caller
     * passes the resolved set so contact matching can be disabled by simply
     * passing an empty set.
     */
    data object Contacts : RuleMatcher

    /**
     * Matches when the canonical number is one of [numbers].
     */
    data class SpecificNumbers(val numbers: Set<String>) : RuleMatcher

    /**
     * A constrained regular expression matched against the canonical digit
     * string. The pattern is validated at compile time: it must parse, fit
     * within [RuleCompiler.MAX_REGEX_LENGTH], and contain no nested
     * repetition that could cause pathological backtracking.
     */
    data class Regex(val pattern: String) : RuleMatcher
}
