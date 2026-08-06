package studio.ainovations.callguard.domain

/**
 * The action a [BlockingRule] takes when its matcher accepts an incoming
 * number. The screening service maps these to platform responses; the domain
 * layer only reasons about the action itself.
 */
enum class RuleAction {
    ALLOW,
    BLOCK,
    SILENCE,
}
