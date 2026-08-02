package studio.ainovations.callguard.phone

/**
 * The canonical, region-resolved form of a parsed phone number.
 *
 * - [e164]: the E.164 form with a leading `+`, e.g. `+15718881234`. This is
 *   the canonical wire format used for storage and comparison.
 * - [digits]: the digit-only canonical form, i.e. [e164] without the leading
 *   `+`, e.g. `15718881234`. This is the value the rule engine matches
 *   against; it never contains formatting characters or a `+`.
 * - [display]: a human-readable formatted form for UI display only, e.g.
 *   `+1 517-888-1234`. It is never used for matching.
 * - [region]: the ISO 3166-1 alpha-2 code of the country the number was
 *   resolved to, e.g. `US`. Empty if the number is valid but maps to no
 *   single region (e.g. a global network code).
 */
data class NormalizedPhone(
    val e164: String,
    val digits: String,
    val display: String,
    val region: String,
)

/**
 * The outcome of normalizing a [PhoneNumberInput].
 *
 * The sealed shape forces callers to handle each case: a successfully parsed
 * number, an unrecoverable parse failure, and a national number that is
 * ambiguous without an explicit country selection.
 */
sealed interface PhoneNormalizationResult {
    /**
     * The input parsed to a single, valid phone number.
     */
    data class Valid(val phone: NormalizedPhone) : PhoneNormalizationResult

    /**
     * The input could not be normalized: it was empty, carried no digits
     * (private/anonymous caller ID), used an unsupported region code, or did
     * not parse to a valid number in any region. [reason] is a plain-language
     * string suitable for surfacing to the user.
     */
    data class Invalid(val reason: String) : PhoneNormalizationResult

    /**
     * The input is a national number with no region context that is valid in
     * more than one country. The caller must ask the user to pick one of
     * [regions] (ISO 3166-1 alpha-2 codes) and re-submit it as
     * [PhoneNumberInput.region].
     */
    data class NeedsRegion(val regions: List<String>) : PhoneNormalizationResult
}
