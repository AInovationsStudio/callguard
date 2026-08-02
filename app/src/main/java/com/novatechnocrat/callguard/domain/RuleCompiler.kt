package studio.ainovations.callguard.domain

import java.math.BigInteger

/**
 * Compiles and evaluates [BlockingRule] sets against a canonical digit string.
 *
 * The compiler is Android-independent: it depends only on the JDK regex
 * engine and the domain types. Validation rejects malformed matchers before
 * evaluation so a caller that compiles at save time can never observe a
 * partially applied rule set at call time.
 *
 * Note: `evaluate` recompiles every enabled rule on each call, which is fine
 * for the rule compiler unit-test surface but wasteful on the screening hot path.
 * persistence layer's `RuleSnapshot` is expected to compile (and cache the compiled
 * [java.util.regex.Pattern] and `Range` bounds) once per atomic snapshot
 * publication and reuse the cached form across evaluations.
 */
object RuleCompiler {

    /** Maximum length of a [RuleMatcher.Regex] pattern, in characters. */
    const val MAX_REGEX_LENGTH: Int = 128

    /**
     * Evaluate [numberDigits] (canonical digit-only form) against [rules],
     * resolving [RuleMatcher.Contacts] against [contacts].
     *
     * Enabled rules whose matcher accepts the number are candidates. Disabled
     * rules are skipped before compilation, so a malformed disabled matcher
     * never throws and never affects evaluation of enabled rules. The
     * winning candidate is chosen by descending explicit [BlockingRule.priority]
     * and then ascending matcher specificity (exact > contacts/specific >
     * prefix/suffix/range/contains > regex). Equal-priority, equal-specificity
     * candidates keep their declaration order. If no candidate matches, the
     * result is a default allow with a null [MatchResult.ruleId].
     *
     * @throws IllegalArgumentException if any enabled rule carries an invalid
     *   matcher value (empty, non-digit canonical value, or an invalid /
     *   over-long / nested-repetition regex).
     */
    fun evaluate(
        numberDigits: String,
        rules: List<BlockingRule>,
        contacts: Set<String>,
    ): MatchResult {
        // Disabled rules are skipped BEFORE compilation so a malformed
        // disabled matcher can never throw or break evaluation of a valid
        // enabled rule. The original declaration index is preserved so equal
        // priority/specificity candidates keep stable order.
        val winner = rules
            .asSequence()
            .mapIndexed { index, rule -> index to rule }
            .filter { (_, rule) -> rule.enabled }
            .map { (index, rule) -> Candidate(index, rule, compile(rule)) }
            .filter { it.compiled.matches(numberDigits, contacts) }
            .minWithOrNull(candidateComparator)

        return if (winner != null) {
            MatchResult(
                action = winner.rule.action,
                ruleId = winner.rule.id,
                explanation = explain(winner.rule, winner.compiled),
            )
        } else {
            MatchResult(
                action = RuleAction.ALLOW,
                ruleId = null,
                explanation = "No enabled rule matched; default allow.",
            )
        }
    }

    private data class Candidate(
        val order: Int,
        val rule: BlockingRule,
        val compiled: CompiledMatcher,
    )

    private val candidateComparator: Comparator<Candidate> =
        compareBy<Candidate> { -it.rule.priority }
            .thenBy { it.compiled.specificity }
            .thenBy { it.order }

    private fun compile(rule: BlockingRule): CompiledMatcher =
        when (val matcher = rule.matcher) {
            is RuleMatcher.Exact -> {
                requireDigits(matcher.value, rule)
                CompiledMatcher(Exactness.EXACT) { n, _ -> n == matcher.value }
            }
            is RuleMatcher.StartsWith -> {
                requireDigits(matcher.prefix, rule)
                CompiledMatcher(Exactness.PREFIX) { n, _ -> n.startsWith(matcher.prefix) }
            }
            is RuleMatcher.EndsWith -> {
                requireDigits(matcher.suffix, rule)
                CompiledMatcher(Exactness.PREFIX) { n, _ -> n.endsWith(matcher.suffix) }
            }
            is RuleMatcher.Contains -> {
                requireDigits(matcher.substring, rule)
                CompiledMatcher(Exactness.PREFIX) { n, _ -> n.contains(matcher.substring) }
            }
            is RuleMatcher.Range -> {
                requireDigits(matcher.startInclusive, rule)
                requireDigits(matcher.endInclusive, rule)
                val start = BigInteger(matcher.startInclusive)
                val end = BigInteger(matcher.endInclusive)
                require(start <= end) { "Range start ${matcher.startInclusive} > end ${matcher.endInclusive} in rule ${rule.id}" }
                CompiledMatcher(Exactness.PREFIX) { n, _ ->
                    if (n.isEmpty()) {
                        false
                    } else {
                        runCatching { BigInteger(n) }.getOrNull()?.let { it in start..end } ?: false
                    }
                }
            }
            is RuleMatcher.Contacts -> {
                CompiledMatcher(Exactness.CONTACT) { n, c -> n.isNotEmpty() && c.contains(n) }
            }
            is RuleMatcher.SpecificNumbers -> {
                require(matcher.numbers.isNotEmpty()) { "SpecificNumbers matcher in rule ${rule.id} has no numbers" }
                matcher.numbers.forEach { requireDigits(it, rule) }
                val set = matcher.numbers.toSet()
                CompiledMatcher(Exactness.CONTACT) { n, _ -> set.contains(n) }
            }
            is RuleMatcher.Regex -> {
                val pattern = compileRegex(matcher.pattern, rule)
                CompiledMatcher(Exactness.REGEX) { n, _ ->
                    if (n.isEmpty()) {
                        pattern.matcher("").matches()
                    } else {
                        pattern.matcher(n).matches()
                    }
                }
            }
        }

    private fun requireDigits(value: String, rule: BlockingRule) {
        require(value.isNotEmpty()) { "Empty canonical value in rule ${rule.id}" }
        require(value.all { it.isDigit() }) { "Non-digit canonical value '$value' in rule ${rule.id}" }
    }

    private fun compileRegex(pattern: String, rule: BlockingRule): java.util.regex.Pattern {
        require(pattern.length <= MAX_REGEX_LENGTH) {
            "Regex pattern in rule ${rule.id} exceeds $MAX_REGEX_LENGTH characters"
        }
        val compiled = try {
            java.util.regex.Pattern.compile(pattern)
        } catch (e: java.util.regex.PatternSyntaxException) {
            throw IllegalArgumentException("Invalid regex '$pattern' in rule ${rule.id}: ${e.message}", e)
        }
        require(!hasNestedRepetition(pattern)) {
            "Regex '$pattern' in rule ${rule.id} contains a nested repetition that may cause pathological backtracking"
        }
        return compiled
    }

    /**
     * Detect a quantifier applied to a group that itself contains a quantifier
     * (e.g. `(\d+)+`, `(a*)*`, `(a{2,3})+`, `((\d+))+`) — the classic
     * catastrophic-backtracking shape. Redundant outer grouping does not
     * bypass the check: a quantifier inside a nested group propagates up to
     * the parent, so a quantifier on the outer group is still flagged.
     * Possessive/reluctant modifiers (`a++`, `a*?`) and dangling quantifiers
     * are left to [java.util.regex.Pattern] to reject.
     */
    private fun hasNestedRepetition(pattern: String): Boolean {
        val groupHasQuantifier = ArrayDeque<Boolean>()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when (c) {
                '\\' -> i += 2
                '(' -> {
                    if (i + 1 < pattern.length && pattern[i + 1] == '?') {
                        // non-capturing / flag / lookahead group: skip the '?'
                        i += 2
                        while (i < pattern.length && pattern[i] != ':' && pattern[i] != ')') i += 1
                        if (i < pattern.length && pattern[i] == ':') i += 1
                        groupHasQuantifier.addLast(false)
                    } else {
                        groupHasQuantifier.addLast(false)
                        i += 1
                    }
                }
                ')' -> {
                    val had = groupHasQuantifier.removeLastOrNull() ?: false
                    i += 1
                    // A nested quantified group makes its parent contain a
                    // quantified subexpression, so the parent must be marked
                    // too — otherwise redundant grouping like `((\d+))+` would
                    // hide the inner quantifier from the outer-group check.
                    if (had && groupHasQuantifier.isNotEmpty()) {
                        groupHasQuantifier[groupHasQuantifier.lastIndex] = true
                    }
                    if (had && i < pattern.length && isQuantifierStart(pattern[i])) return true
                }
                '[' -> {
                    var j = pattern.indexOf(']', i + 1)
                    if (j == i + 1) j = pattern.indexOf(']', i + 2)
                    i = if (j >= 0) j + 1 else pattern.length
                }
                '{' -> {
                    val close = pattern.indexOf('}', i + 1)
                    if (close > i && pattern.substring(i + 1, close).matches(Regex("\\d+(,\\d*)?"))) {
                        if (groupHasQuantifier.isNotEmpty()) groupHasQuantifier[groupHasQuantifier.lastIndex] = true
                        i = close + 1
                    } else {
                        i += 1
                    }
                }
                '*', '+', '?' -> {
                    if (groupHasQuantifier.isNotEmpty()) groupHasQuantifier[groupHasQuantifier.lastIndex] = true
                    i += 1
                }
                else -> i += 1
            }
        }
        return false
    }

    private fun isQuantifierStart(c: Char): Boolean = c == '*' || c == '+' || c == '?' || c == '{'

    private fun explain(rule: BlockingRule, compiled: CompiledMatcher): String {
        val matcherPhrase = when (val m = rule.matcher) {
            is RuleMatcher.Exact -> "exactly ${m.value}"
            is RuleMatcher.StartsWith -> "starts with ${m.prefix}"
            is RuleMatcher.EndsWith -> "ends with ${m.suffix}"
            is RuleMatcher.Contains -> "contains ${m.substring}"
            is RuleMatcher.Range -> "in range ${m.startInclusive}..${m.endInclusive}"
            is RuleMatcher.Contacts -> "in contacts"
            is RuleMatcher.SpecificNumbers -> "a specific number"
            is RuleMatcher.Regex -> "matches regex ${m.pattern}"
        }
        return "Rule '${rule.name}' (${rule.id}) ${rule.action.name.lowercase()} — ${compiled.specificity.label} match: $matcherPhrase."
    }

    private data class CompiledMatcher(
        val specificity: Exactness,
        val matches: (number: String, contacts: Set<String>) -> Boolean,
    )

    private enum class Exactness(val label: String) {
        EXACT("exact"),
        CONTACT("contact"),
        PREFIX("prefix/suffix/range/contains"),
        REGEX("regex"),
    }
}
