package studio.ainovations.callguard.screening

import studio.ainovations.callguard.data.CallGuardPreferences
import studio.ainovations.callguard.data.RuleSnapshot
import studio.ainovations.callguard.domain.MatchResult
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
    ): MatchResult {
        val effectiveRegion = region ?: preferences.defaultRegion
        if (rawNumber.isNullOrBlank()) {
            return fallback(preferences)
        }
        if (snapshot.hasContactRules && preferences.contactMatchingEnabled && !contactsAvailable) {
            return MatchResult(
                action = preferences.unknownNumberAction,
                ruleId = null,
                explanation = "Contact matching is unavailable; the configured fallback action was applied.",
            )
        }

        return when (
            val normalized = normalizer.normalize(
                PhoneNumberInput(raw = rawNumber, region = effectiveRegion),
            )
        ) {
            is PhoneNormalizationResult.Valid -> snapshot.evaluate(
                numberDigits = normalized.phone.digits,
                contacts = if (preferences.contactMatchingEnabled) contacts else emptySet(),
            )
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
