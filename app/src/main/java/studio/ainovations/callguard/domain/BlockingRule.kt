package studio.ainovations.callguard.domain

/**
 * A user-authored call rule. Rules are evaluated by [RuleCompiler] in
 * priority-then-specificity order; the [matcher] decides whether a given
 * canonical number is a candidate.
 */
data class BlockingRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val action: RuleAction,
    val matcher: RuleMatcher,
    val priority: Int,
)
