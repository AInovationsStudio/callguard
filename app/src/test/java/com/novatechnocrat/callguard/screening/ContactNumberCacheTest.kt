package studio.ainovations.callguard.screening

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactNumberCacheTest {
    @Test
    fun refreshPublishesNumbersUsingTheRequestedRegion() = runBlocking {
        val provider = RecordingContactProvider(
            numbersByRegion = mapOf("GB" to setOf("441234567890")),
        )
        val cache = ContactNumberCache(provider)

        cache.refresh("GB")

        assertEquals(listOf("GB"), provider.requestedRegions)
        assertTrue(cache.current().available)
        assertEquals("GB", cache.current().region)
        assertEquals(setOf("441234567890"), cache.current().numbers)
    }

    @Test
    fun providerFailurePublishesUnavailableStateInsteadOfStaleNumbers() = runBlocking {
        val provider = RecordingContactProvider(
            numbersByRegion = mapOf("US" to setOf("15718881234")),
            failureRegions = setOf("GB"),
        )
        val cache = ContactNumberCache(provider)
        cache.refresh("US")
        cache.refresh("GB")

        assertFalse(cache.current().available)
        assertEquals("GB", cache.current().region)
        assertEquals(emptySet<String>(), cache.current().numbers)
    }

    private class RecordingContactProvider(
        private val numbersByRegion: Map<String, Set<String>>,
        private val failureRegions: Set<String> = emptySet(),
    ) : ContactNumberProvider {
        val requestedRegions = mutableListOf<String?>()

        override fun loadCanonicalNumbers(region: String?): Set<String> {
            requestedRegions += region
            if (region in failureRegions) error("contacts unavailable")
            return numbersByRegion[region].orEmpty()
        }
    }
}
