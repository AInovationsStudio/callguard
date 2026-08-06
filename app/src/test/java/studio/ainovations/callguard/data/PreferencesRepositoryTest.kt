package studio.ainovations.callguard.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import studio.ainovations.callguard.domain.RuleAction

/**
 * Unit tests for [PreferencesRepository] against an in-memory [FakeDataStore].
 *
 * Beyond the persistence layer brief's required [RuleRepositoryTest]: [PreferencesRepository]
 * is simple enough to test hermetically (no file I/O, no Android runtime)
 * that skipping coverage would leave a new production file untested.
 */
class PreferencesRepositoryTest {

    /** An in-memory stand-in for a real Jetpack DataStore<Preferences>. */
    private class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    @Test
    fun defaultsWhenNothingStored() = runBlocking {
        val repo = PreferencesRepository(FakeDataStore())
        val prefs = repo.preferences.first()

        assertNull(prefs.defaultRegion)
        assertEquals(RuleAction.ALLOW, prefs.unknownNumberAction)
        assertFalse(prefs.contactMatchingEnabled)
    }

    @Test
    fun setDefaultRegionRoundTripsAndCanBeCleared() = runBlocking {
        val repo = PreferencesRepository(FakeDataStore())

        repo.setDefaultRegion("GB")
        assertEquals("GB", repo.preferences.first().defaultRegion)

        repo.setDefaultRegion(null)
        assertNull(repo.preferences.first().defaultRegion)
    }

    @Test
    fun setUnknownNumberActionRoundTrips() = runBlocking {
        val repo = PreferencesRepository(FakeDataStore())

        repo.setUnknownNumberAction(RuleAction.BLOCK)
        assertEquals(RuleAction.BLOCK, repo.preferences.first().unknownNumberAction)
    }

    @Test
    fun setContactMatchingEnabledRoundTrips() = runBlocking {
        val repo = PreferencesRepository(FakeDataStore())

        repo.setContactMatchingEnabled(true)
        assertEquals(true, repo.preferences.first().contactMatchingEnabled)

        repo.setContactMatchingEnabled(false)
        assertEquals(false, repo.preferences.first().contactMatchingEnabled)
    }

    @Test
    fun bootstrapPreferencesReadsPersistedValuesBeforeTheFirstEvaluation() = runBlocking {
        val store = FakeDataStore()
        PreferencesRepository(store).apply {
            setDefaultRegion("GB")
            setUnknownNumberAction(RuleAction.BLOCK)
            setContactMatchingEnabled(true)
        }

        val freshProcess = PreferencesRepository(store)
        val preferences = freshProcess.bootstrapPreferences()

        assertEquals("GB", preferences.defaultRegion)
        assertEquals(RuleAction.BLOCK, preferences.unknownNumberAction)
        assertEquals(true, preferences.contactMatchingEnabled)
    }
}
