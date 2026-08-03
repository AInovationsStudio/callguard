package studio.ainovations.callguard.screening

import android.content.Context
import android.telephony.TelephonyManager

/**
 * Uses the SIM/network region first, then the device locale. Both the editor
 * and screening service use this exact fallback order.
 */
fun deviceRegionFor(context: Context): String? {
    val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    val simRegion = telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
    if (simRegion != null) return simRegion.uppercase()
    return context.resources.configuration.locales.get(0)?.country
        ?.takeIf { it.isNotBlank() }
        ?.uppercase()
}
