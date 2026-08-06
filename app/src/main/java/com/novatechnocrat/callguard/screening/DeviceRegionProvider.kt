package studio.ainovations.callguard.screening

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Uses the network region first, then the SIM region, then the configured and
 * default locale. Both the editor and screening service use this exact
 * fallback order.
 */
fun deviceRegionFor(context: Context): String? {
    val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    val simRegion = telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
    val simCardRegion = telephony?.simCountryIso?.takeIf { it.isNotBlank() }
    val configuredLocaleRegion = context.resources.configuration.locales
        .get(0)
        ?.country
        ?.takeIf { it.isNotBlank() }
    return selectDeviceRegion(
        networkCountryIso = simRegion,
        simCountryIso = simCardRegion,
        localeCountry = configuredLocaleRegion ?: Locale.getDefault().country,
    )
}

internal fun selectDeviceRegion(
    networkCountryIso: String?,
    simCountryIso: String?,
    localeCountry: String?,
): String? = listOf(networkCountryIso, simCountryIso, localeCountry)
    .firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty)?.uppercase(Locale.ROOT) }
