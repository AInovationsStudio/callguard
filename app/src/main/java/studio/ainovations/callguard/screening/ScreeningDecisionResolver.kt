package studio.ainovations.callguard.screening

import studio.ainovations.callguard.data.CallGuardPreferences
import studio.ainovations.callguard.data.RuleSnapshot
import studio.ainovations.callguard.domain.MatchResult
import studio.ainovations.callguard.domain.RuleAction
import studio.ainovations.callguard.phone.PhoneNormalizationResult
import studio.ainovations.callguard.phone.PhoneNormalizer
import studio.ainovations.callguard.phone.PhoneNumberInput

/**
 * Resolves one incoming caller identity against the immutable screening
 * snapshot. This class is Android-free so the safety/default behavior is
 * unit-testable independently from [android.telecom.CallScreeningService].
 */
class ScreeningDecisionResolver(
    private val normalizer: PhoneNormalizer,
) {
    fun resolve(
        snapshot: RuleSnapshot,
        rawNumber: String?,
        region: String?,
        preferences: CallGuardPreferences,
        contacts: Set<String>,
        contactsAvailable: Boolean = true,
        runtimeLoaded: Boolean = true,
    ): MatchResult {
        if (!runtimeLoaded) {
            return MatchResult(
                action = RuleAction.ALLOW,
                ruleId = null,
                explanation = "Screening is not initialized; the phone's normal behavior was preserved.",
            )
        }
        val effectiveRegion = region ?: preferences.defaultRegion
        if (rawNumber.isNullOrBlank()) {
            return fallback(preferences)
        }
        val contactMatchingRequired =
            snapshot.hasContactRules && preferences.contactMatchingEnabled && !contactsAvailable
        val effectiveContacts =
            if (preferences.contactMatchingEnabled && contactsAvailable) contacts else emptySet()

        return when (
            val normalized = normalizer.normalize(
                PhoneNumberInput(raw = rawNumber, region = effectiveRegion),
            )
        ) {
            is PhoneNormalizationResult.Valid -> {
                val matched = snapshot.evaluate(
                    numberDigits = normalized.phone.digits,
                    contacts = effectiveContacts,
                )
                when {
                    matched.ruleId != null -> matched
                    contactMatchingRequired -> MatchResult(
                        action = preferences.unknownNumberAction,
                        ruleId = null,
                        explanation = "Contact matching is unavailable; the configured fallback action was applied.",
                    )
                    else -> matched
                }
            }
            is PhoneNormalizationResult.Invalid,
            is PhoneNormalizationResult.NeedsRegion,
            -> fallback(preferences)
        }
    }

    private fun fallback(preferences: CallGuardPreferences): MatchResult =
        MatchResult(
            action = preferences.unknownNumberAction,
            ruleId = null,
            explanation = "Caller identity was unavailable or could not be parsed; " +
                "the configured unknown-number action was applied.",
        )
}
