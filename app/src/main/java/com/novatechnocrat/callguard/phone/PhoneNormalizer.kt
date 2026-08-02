package studio.ainovations.callguard.phone

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber

/**
 * An Android-independent adapter around libphonenumber that parses national
 * and international phone numbers and emits canonical digit strings.
 *
 * Country metadata is bundled through the `libphonenumber` dependency; the
 * adapter never makes a runtime network request. Region resolution order:
 *
 * 1. The explicit [PhoneNumberInput.region] supplied by the caller (e.g. the
 *    country the user picked in the wizard).
 * 2. The device-region adapter supplied by the caller via [deviceRegion]
 *    (e.g. the telephony country code resolved from the SIM/locale).
 * 3. An ambiguity scan over libphonenumber's supported regions.
 *
 * When neither an explicit nor a device region is available and the input is
 * a national number valid in more than one country, [normalize] returns
 * [PhoneNormalizationResult.NeedsRegion] so the UI can ask the user to pick.
 * International input (a `+`-prefixed E.164 string) is parsed without any
 * region hint and never produces [PhoneNormalizationResult.NeedsRegion].
 *
 * The constructor-injected [deviceRegion] and [util] defaults keep this class
 * Android-independent and unit-testable on the JVM: the production caller
 * supplies the real device-region adapter, tests supply `null` or a fixed
 * region.
 */
class PhoneNormalizer(
    private val deviceRegion: () -> String? = { null },
    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance(),
) {
    /**
     * Normalize [input] to a canonical phone number, an invalid result, or a
     * request for an explicit region.
     */
    fun normalize(input: PhoneNumberInput): PhoneNormalizationResult {
        val raw = input.raw.trim()
        if (raw.isEmpty()) {
            return PhoneNormalizationResult.Invalid("empty phone number")
        }
        // Private/anonymous caller IDs ("PRIVATE", "UNKNOWN", "anonymous",
        // "--") carry no digits and cannot be normalized to a canonical
        // number. Fail closed rather than inventing digits.
        if (raw.none { it.isDigit() }) {
            return PhoneNormalizationResult.Invalid(
                "private or unknown caller id: no digits in '$raw'",
            )
        }

        val explicitRegion = input.region?.takeIf { it.isNotBlank() }?.uppercase()
        val fallbackRegion = deviceRegion()?.takeIf { it.isNotBlank() }?.uppercase()
        val effectiveRegion: String? = explicitRegion ?: fallbackRegion

        if (effectiveRegion != null && !util.supportedRegions.contains(effectiveRegion)) {
            return PhoneNormalizationResult.Invalid(
                "unsupported region code '$effectiveRegion'",
            )
        }

        val isInternational = raw.startsWith("+")
        if (isInternational || effectiveRegion != null) {
            // libphonenumber ignores the default region for `+`-prefixed input,
            // so passing null/empty is safe for international numbers; for
            // national numbers we pass the resolved region.
            val defaultRegion = if (isInternational) null else effectiveRegion
            val parsed = try {
                util.parse(raw, defaultRegion ?: "")
            } catch (e: NumberParseException) {
                return PhoneNormalizationResult.Invalid(
                    "could not parse '$raw': ${e.message}",
                )
            }
            if (!util.isValidNumber(parsed)) {
                return PhoneNormalizationResult.Invalid(
                    "not a valid phone number: '$raw'",
                )
            }
            return valid(parsed)
        }

        // No region context at all: scan libphonenumber's supported regions
        // for countries where the national number is valid. Zero matches is
        // invalid; one is a deterministic parse; more than one is ambiguous.
        val candidates = supportedRegionsFor(raw)
        return when {
            candidates.isEmpty() -> PhoneNormalizationResult.Invalid(
                "no region matched national number '$raw'",
            )
            candidates.size == 1 -> valid(util.parse(raw, candidates.first()))
            else -> PhoneNormalizationResult.NeedsRegion(candidates.sorted())
        }
    }

    private fun valid(parsed: Phonenumber.PhoneNumber): PhoneNormalizationResult.Valid {
        val e164 = util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        val digits = e164.removePrefix("+")
        val display = util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
        val region = util.getRegionCodeForNumber(parsed) ?: ""
        return PhoneNormalizationResult.Valid(
            NormalizedPhone(e164 = e164, digits = digits, display = display, region = region),
        )
    }

    private fun supportedRegionsFor(raw: String): List<String> {
        // Deduplicate by the canonical E.164 so that regions which parse the
        // number to the *same* international form count once; only genuinely
        // different resolutions remain as distinct candidates.
        val seen = LinkedHashMap<String, String>()
        for (region in util.supportedRegions) {
            val parsed = try {
                util.parse(raw, region)
            } catch (e: NumberParseException) {
                null
            } ?: continue
            if (!util.isValidNumber(parsed)) continue
            val e164 = util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            seen.putIfAbsent(e164, region)
        }
        return seen.values.toList()
    }
}
