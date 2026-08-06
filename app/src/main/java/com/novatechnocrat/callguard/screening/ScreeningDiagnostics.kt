package studio.ainovations.callguard.screening

import studio.ainovations.callguard.domain.RuleAction

/**
 * Debug-only screening diagnostics deliberately exclude handles, numbers,
 * contact identities, rule names, and exception messages.
 */
internal object ScreeningDiagnostics {
    fun bootstrap(loaded: Boolean, ruleCount: Int): String =
        "bootstrap loaded=$loaded ruleCount=$ruleCount"

    fun entry(loaded: Boolean, ruleCount: Int): String =
        "entry loaded=$loaded ruleCount=$ruleCount"

    fun failure(error: Throwable): String =
        "bootstrap failed exception=${error::class.java.name}"

    fun decision(
        loaded: Boolean,
        ruleCount: Int,
        action: RuleAction,
        ruleId: String?,
    ): String =
        "decision loaded=$loaded ruleCount=$ruleCount action=$action ruleId=$ruleId"
}
