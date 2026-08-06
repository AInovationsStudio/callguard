package studio.ainovations.callguard.domain

/**
 * The outcome of evaluating a number against a rule set. The screening service
 * consumes [action]; the UI surfaces [explanation]. When no rule matches,
 * [ruleId] is null and [action] is [RuleAction.ALLOW] with a default-allow
 * explanation.
 */
data class MatchResult(
    val action: RuleAction,
    val ruleId: String?,
    val explanation: String,
)
