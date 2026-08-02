package studio.ainovations.callguard.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the Android-independent libphonenumber adapter. No
 * Android types are referenced here or in the `phone` package adapter code.
 *
 * Canonical-digit expectations below come from libphonenumber 8.13.55's
 * metadata for the chosen regions; the version is pinned in
 * `gradle/libs.versions.toml`, so these expectations are deterministic.
 */
class PhoneNormalizerTest {

    private val noDeviceRegion: () -> String? = { null }

    // A US national number used across several tests: (517) 888-1234 -> +1 517
    // 888 1234 -> canonical digits "15178881234".
    private val usNational = "(517) 888-1234"
    private val usDigits = "15178881234"
    private val usE164 = "+15178881234"

    // --- Valid international input ---

    @Test
    fun internationalE164ParsesToCanonicalDigits() {
        val normalizer = PhoneNormalizer(deviceRegion = noDeviceRegion)
        val result = normalizer.normalize(PhoneNumberInput(usE164, region = null))
        assertTrue(result is PhoneNormalizationResult.Valid)
        val phone = (result as PhoneNormalizationResult.Valid).phone
        assertEquals(usE164, phone.e164)
        assertEquals(usDigits, phone.digits)
        assertEquals("US", phone.region)
        assertTrue(phone.digits.all { it.isDigit() })
        assertTrue(phone.display.contains("+1"))
    }

    @Test
    fun internationalInputIgnoresExplicitRegion() {
        // A + number resolves from its country code; the supplied region must
        // not change the canonical digits.
        val normalizer = PhoneNormalizer(deviceRegion = noDeviceRegion)
        val r1 = normalizer.normalize(PhoneNumberInput(usE164, region = "GB"))
        val r2 = normalizer.normalize(PhoneNumberInput(usE164, region = "US"))
        assertEquals(
            (r1 as PhoneNormalizationResult.Valid).phone.digits,
            (r2 as PhoneNormalizationResult.Valid).phone.digits,
        )
    }

    // --- Valid national input with explicit region ---

    @Test
    fun nationalInputWithExplicitRegionParses() {
        val normalizer = PhoneNormalizer(deviceRegion = noDeviceRegion)
        val result = normalizer.normalize(PhoneNumberInput(usNational, region = "US"))
        assertTrue(result is PhoneNormalizationResult.Valid)
        val phone = (result as PhoneNormalizationResult.Valid).phone
        assertEquals(usDigits, phone.digits)
        assertEquals(usE164, phone.e164)
        assertEquals("US", phone.region)
    }

    @Test
    fun nationalInputWithExplicitLowercaseRegionIsNormalized() {
        val normalizer = PhoneNormalizer(deviceRegion = noDeviceRegion)
        val result = normalizer.normalize(PhoneNumberInput("517-888-1234", region = "us"))
        assertTrue(result is PhoneNormalizationResult.Valid)
        assertEquals(usDigits, (result as PhoneNormalizationResult.Valid).phone.digits)
    }

    // --- Default (device) region fallback ---

    @Test
    fun nationalInputFallsBackToDeviceRegion() {
        // No explicit region; the device-region adapter supplies "US".
        val normalizer = PhoneNormalizer(deviceRegion = { "US" })
        val result = normalizer.normalize(PhoneNumberInput("5178881234", region = null))
        assertTrue(result is PhoneNormalizationResult.Valid)
        assertEquals(usDigits, (result as PhoneNormalizationResult.Valid).phone.digits)
    }

    @Test
    fun explicitRegionBeatsDeviceRegion() {
        val normalizer = PhoneNormalizer(deviceRegion = { "GB" })
        val result = normalizer.normalize(PhoneNumberInput(usNational, region = "US"))
        assertTrue(result is PhoneNormalizationResult.Valid)
        assertEquals(usDigits, (result as PhoneNormalizationResult.Valid).phone.digits)
    }

    // --- Same number in multiple display formats -> same canonical digits ---

    @Test
    fun multipleDisplayFormatsNormalizeToSameCanonicalDigits() {
        val normalizer = PhoneNormalizer(deviceRegion = { "US" })
        val forms = listOf(
            usE164,
            "1 (517) 888-1234",
            "1-517-888-1234",
            "5178881234",
            "+1 517 888 1234",
        )
        val digits = forms.map {
            val r = normalizer.normalize(PhoneNumberInput(it, region = null))
            assertTrue("expected valid for '$it': $r", r is PhoneNormalizationResult.Valid)
            (r as PhoneNormalizationResult.Valid).phone.digits
        }.toSet()
        assertEquals(setOf(usDigits), digits)
    }

    // --- Ambiguous national input without region ---

    @Test
    fun ambiguousNationalInputWithoutRegionRequestsRegion() {
        // No explicit region, no device region. A national number that is
        // valid in more than one country must surface as NeedsRegion.
        val normalizer = PhoneNormalizer(deviceRegion = noDeviceRegion)
        val result = normalizer.normalize(PhoneNumberInput("020 7946 0958", region = null))
        assertTrue("expected NeedsRegion, got $result", result is PhoneNormalizationResult.NeedsRegion)
        val regions = (result as PhoneNormalizationResult.NeedsRegion).regions
        assertTrue("expected >=2 candidate regions, got $regions", regions.size >= 2)
        // GB is a known match for this London landline form.
        assertTrue("GB should be a candidate, got $regions", regions.contains("GB"))
        // All candidates are uppercase 2-letter region codes.
        assertTrue(regions.all { it.length == 2 && it.uppercase() == it })
    }

    // --- Invalid input ---

    @Test
    fun invalidInternationalNumberIsRejected() {
        val normalizer = PhoneNormalizer(deviceRegion = noDeviceRegion)
        val result = normalizer.normalize(PhoneNumberInput("+1999", region = null))
        assertTrue(result is PhoneNormalizationResult.Invalid)
    }

    @Test
    fun garbageInputIsRejected() {
        val normalizer = PhoneNormalizer(deviceRegion = noDeviceRegion)
        val result = normalizer.normalize(PhoneNumberInput("not a number at all", region = "US"))
        assertTrue(result is PhoneNormalizationResult.Invalid)
    }

    @Test
    fun unsupportedRegionCodeIsRejected() {
        val normalizer = PhoneNormalizer(deviceRegion = noDeviceRegion)
        val result = normalizer.normalize(PhoneNumberInput("5178881234", region = "ZZZ"))
        assertTrue(result is PhoneNormalizationResult.Invalid)
        assertTrue(
            (result as PhoneNormalizationResult.Invalid).reason.contains("unsupported region"),
        )
    }

    @Test
    fun nationalNumberValidNowhereIsRejected() {
        // No region context; a string that is not a valid national number in
        // any country is invalid, not ambiguous.
        val normalizer = PhoneNormalizer(deviceRegion = noDeviceRegion)
        val result = normalizer.normalize(PhoneNumberInput("12345678901234567890", region = null))
        assertTrue(result is PhoneNormalizationResult.Invalid)
    }

    // --- Private / empty input ---

    @Test
    fun emptyInputIsInvalid() {
        val normalizer = PhoneNormalizer(deviceRegion = noDeviceRegion)
        val result = normalizer.normalize(PhoneNumberInput("", region = null))
        assertTrue(result is PhoneNormalizationResult.Invalid)
    }

    @Test
    fun blankInputIsInvalid() {
        val normalizer = PhoneNormalizer(deviceRegion = noDeviceRegion)
        val result = normalizer.normalize(PhoneNumberInput("   ", region = null))
        assertTrue(result is PhoneNormalizationResult.Invalid)
    }

    @Test
    fun privateCallerIdIsInvalid() {
        val normalizer = PhoneNormalizer(deviceRegion = noDeviceRegion)
        for (raw in listOf("PRIVATE", "UNKNOWN", "anonymous", "--", "withheld")) {
            val result = normalizer.normalize(PhoneNumberInput(raw, region = null))
            assertTrue("'$raw' should be Invalid, got $result", result is PhoneNormalizationResult.Invalid)
        }
    }

    // --- Canonical digit output ---

    @Test
    fun canonicalDigitsAreDigitOnlyForAllValidInputs() {
        val normalizer = PhoneNormalizer(deviceRegion = { "US" })
        val forms = listOf(
            PhoneNumberInput(usE164, region = null),
            PhoneNumberInput(usNational, region = "US"),
            PhoneNumberInput("5178881234", region = null),
            // A UK London landline in international form.
            PhoneNumberInput("+442079460958", region = null),
        )
        for (form in forms) {
            val result = normalizer.normalize(form)
            assertTrue("expected valid for $form, got $result", result is PhoneNormalizationResult.Valid)
            val digits = (result as PhoneNormalizationResult.Valid).phone.digits
            assertTrue("digits must be non-empty: $form", digits.isNotEmpty())
            assertTrue("digits must be digit-only: '$digits' for $form", digits.all { it.isDigit() })
            assertTrue("digits must not contain '+': '$digits'", !digits.contains("+"))
        }
    }

    @Test
    fun e164IsPlusPrefixedAndDigitsAreNot() {
        val normalizer = PhoneNormalizer(deviceRegion = noDeviceRegion)
        val result = normalizer.normalize(PhoneNumberInput("+442079460958", region = null))
        assertTrue(result is PhoneNormalizationResult.Valid)
        val phone = (result as PhoneNormalizationResult.Valid).phone
        assertTrue(phone.e164.startsWith("+"))
        assertEquals(phone.e164.removePrefix("+"), phone.digits)
        assertEquals("GB", phone.region)
    }
}
