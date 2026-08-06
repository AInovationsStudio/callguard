package studio.ainovations.callguard.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceRegionProviderTest {
    @Test
    fun networkRegionHasPriorityOverSimAndLocale() {
        assertEquals(
            "GB",
            selectDeviceRegion(networkCountryIso = "gb", simCountryIso = "us", localeCountry = "CA"),
        )
    }

    @Test
    fun simRegionFillsMissingNetworkRegion() {
        assertEquals(
            "US",
            selectDeviceRegion(networkCountryIso = "", simCountryIso = "us", localeCountry = "CA"),
        )
    }

    @Test
    fun localeRegionFillsMissingNetworkAndSimRegions() {
        assertEquals(
            "CA",
            selectDeviceRegion(networkCountryIso = null, simCountryIso = "", localeCountry = "ca"),
        )
    }

    @Test
    fun noUsableRegionRemainsUnknown() {
        assertNull(selectDeviceRegion(networkCountryIso = null, simCountryIso = " ", localeCountry = ""))
    }
}
