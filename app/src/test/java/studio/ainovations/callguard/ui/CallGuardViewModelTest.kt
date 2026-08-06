package studio.ainovations.callguard.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.ainovations.callguard.data.PreferencesRepository
import studio.ainovations.callguard.data.RuleDao
import studio.ainovations.callguard.data.RuleEntity
import studio.ainovations.callguard.data.RuleRepository
import studio.ainovations.callguard.domain.BlockingRule
import studio.ainovations.callguard.domain.RuleAction
import studio.ainovations.callguard.domain.RuleMatcher
import studio.ainovations.callguard.phone.PhoneNormalizer

class CallGuardViewModelTest {
    @Test
    fun generatedRuleNameUsesThePersistedMatcherKind() {
        assertEquals(
            "Block: Contains",
            defaultRuleNameForMatcher(RuleMatcher.Contains("1888"), RuleAction.BLOCK),
        )
        assertEquals(
            "Block: Ends with",
            defaultRuleNameForMatcher(RuleMatcher.EndsWith("1234"), RuleAction.BLOCK),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun rapidTogglesAreAppliedToTheLatestCommittedRuleList() = runBlocking {
        val dao = FakeRuleDao()
        val repository = RuleRepository(dao)
        val initialRules = listOf(
            rule("one", "1571888"),
            rule("two", "1800555"),
        )
        repository.replaceRules(initialRules)
        val firstStateRead = CompletableDeferred<Unit>()
        val releaseFirstStateRead = CompletableDeferred<Unit>()
        val secondStateRead = CompletableDeferred<Unit>()
        var isFirstStateRead = true
        val viewModel = CallGuardViewModel(
            ruleRepository = repository,
            preferencesRepository = PreferencesRepository(FakeDataStore()),
            normalizer = PhoneNormalizer(deviceRegion = { "US" }),
            scope = CoroutineScope(Dispatchers.Default.limitedParallelism(2)),
            afterMutationStateRead = {
                if (isFirstStateRead) {
                    isFirstStateRead = false
                    firstStateRead.complete(Unit)
                    releaseFirstStateRead.await()
                } else {
                    secondStateRead.complete(Unit)
                }
            },
        )

        withTimeout(3_000) {
            while (viewModel.uiState.value.rules.size != 2) delay(10)
        }
        viewModel.onRuleToggled("one", enabled = false)
        firstStateRead.await()
        viewModel.onRuleToggled("two", enabled = false)

        assertNull(
            "second mutation must not read state while first mutation is suspended",
            withTimeoutOrNull(250) { secondStateRead.await() },
        )
        releaseFirstStateRead.complete(Unit)
        withTimeout(3_000) {
            while (dao.rules().map { it.id } != listOf("one", "two") ||
                dao.rules().any { it.enabled }
            ) {
                delay(10)
            }
        }
        assertEquals(listOf("one", "two"), dao.rules().map { it.id })
        assertFalse(dao.rules().any { it.enabled })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun revokingContactsPermissionClearsSilentlyRetainedContactMatching() = runBlocking {
        val dataStore = FakeDataStore()
        val granted = java.util.concurrent.atomic.AtomicBoolean(true)
        val viewModel = CallGuardViewModel(
            ruleRepository = RuleRepository(FakeRuleDao()),
            preferencesRepository = PreferencesRepository(dataStore),
            normalizer = PhoneNormalizer(deviceRegion = { "US" }),
            contactsPermissionGranted = { granted.get() },
            scope = CoroutineScope(Dispatchers.Default.limitedParallelism(2)),
        )

        // Establish the precondition: contacts access is granted and the user
        // has turned contact matching on, so a stale "enabled" preference is
        // persisted in DataStore.
        viewModel.onContactMatchingToggled(true)
        withTimeout(3_000) {
            while (!viewModel.uiState.value.settings.contactMatchingEnabled) delay(10)
        }
        assertTrue(viewModel.uiState.value.settings.contactsPermissionGranted)
        assertTrue(viewModel.uiState.value.settings.contactMatchingEnabled)
        assertEquals(true, dataStore.data.first()[KEY_CONTACT_MATCHING_ENABLED])

        // Revoke contacts permission (the platform callback now reports false).
        granted.set(false)
        viewModel.refreshPermissionState()

        // The UI state must make the disabled condition explicit: contact
        // matching is off, not silently retained.
        assertFalse(viewModel.uiState.value.settings.contactsPermissionGranted)
        assertFalse(
            "contact matching must be cleared in UI state after revocation",
            viewModel.uiState.value.settings.contactMatchingEnabled,
        )

        // The persisted preference must also be explicitly disabled so the
        // screening service (which reads DataStore) cannot leave matching
        // silently on after revocation.
        withTimeout(3_000) {
            while (dataStore.data.first()[KEY_CONTACT_MATCHING_ENABLED] != false) delay(10)
        }
        assertEquals(false, dataStore.data.first()[KEY_CONTACT_MATCHING_ENABLED])
    }

    private fun rule(id: String, prefix: String) = BlockingRule(
        id = id,
        name = id,
        enabled = true,
        action = RuleAction.BLOCK,
        matcher = RuleMatcher.StartsWith(prefix),
        priority = 0,
    )

    private class FakeRuleDao : RuleDao {
        private val state = MutableStateFlow<List<RuleEntity>>(emptyList())

        override fun observeAll(): Flow<List<RuleEntity>> = state

        override suspend fun getAllOnce(): List<RuleEntity> = state.value

        override suspend fun insertAll(entities: List<RuleEntity>) {
            state.value = state.value + entities
        }

        override suspend fun deleteAll() {
            state.value = emptyList()
        }

        fun rules(): List<RuleEntity> = state.value
    }

    private class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private companion object {
        val KEY_CONTACT_MATCHING_ENABLED = booleanPreferencesKey("contact_matching_enabled")
    }
}
