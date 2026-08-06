package studio.ainovations.callguard.phone

/**
 * A raw phone-number string plus an optional explicit region hint.
 *
 * The [raw] string is exactly what the caller or user supplied: it may be in
 * international E.164 form (`+15718881234`), a national form with formatting
 * (`(517) 888-1234`), or an opaque caller-ID token such as `PRIVATE` or
 * `UNKNOWN`. The [region], when non-null, is an ISO 3166-1 alpha-2 country
 * code (`US`, `GB`, ...) that the normalizer uses as the default country when
 * [raw] is a national number. A null [region] means "no explicit country was
 * supplied"; the normalizer then falls back to the device-region adapter
 * supplied by the caller, and finally to an ambiguity scan.
 */
data class PhoneNumberInput(
    val raw: String,
    val region: String?,
)
