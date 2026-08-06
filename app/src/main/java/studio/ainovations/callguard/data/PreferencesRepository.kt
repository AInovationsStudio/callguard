package studio.ainovations.callguard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import studio.ainovations.callguard.domain.RuleAction

private const val CALL_GUARD_DATASTORE_NAME = "callguard_preferences"

/**
 * Top-level DataStore delegate. Call it once at the top level of a Kotlin
 * file (per the DataStore convention) and access `context.callGuardDataStore`
 * throughout the app so it stays a singleton.
 */
val Context.callGuardDataStore: DataStore<Preferences> by preferencesDataStore(name = CALL_GUARD_DATASTORE_NAME)

/**
 * User preferences that are not [studio.ainovations.callguard.domain.BlockingRule]s:
 * the default phone-number region, the action for an unknown/unparseable
 * number, and whether contact matching is enabled. CallGuard does not persist
 * call-screening history; there is no field here for it, and none of this
 * repository's state is call/contact data itself.
 */
data class CallGuardPreferences(
    val defaultRegion: String?,
    val unknownNumberAction: RuleAction,
    val contactMatchingEnabled: Boolean,
) {
    companion object {
        /** Per the design spec: "The default [unknown-number action] is allow." */
        val DEFAULT_UNKNOWN_NUMBER_ACTION = RuleAction.ALLOW
    }
}

/**
 * Reads and writes [CallGuardPreferences] via Jetpack DataStore Preferences.
 */
class PreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val preferences: Flow<CallGuardPreferences> = dataStore.data.map { prefs ->
        CallGuardPreferences(
            defaultRegion = prefs[KEY_DEFAULT_REGION],
            unknownNumberAction = prefs[KEY_UNKNOWN_NUMBER_ACTION]
                ?.let { runCatching { RuleAction.valueOf(it) }.getOrNull() }
                ?: CallGuardPreferences.DEFAULT_UNKNOWN_NUMBER_ACTION,
            contactMatchingEnabled = prefs[KEY_CONTACT_MATCHING_ENABLED] ?: false,
        )
    }

    /** Reads the persisted preferences once during screening-service bootstrap. */
    suspend fun bootstrapPreferences(): CallGuardPreferences = preferences.first()

    suspend fun setDefaultRegion(region: String?) {
        dataStore.edit { prefs ->
            if (region.isNullOrEmpty()) prefs.remove(KEY_DEFAULT_REGION) else prefs[KEY_DEFAULT_REGION] = region
        }
    }

    suspend fun setUnknownNumberAction(action: RuleAction) {
        dataStore.edit { prefs -> prefs[KEY_UNKNOWN_NUMBER_ACTION] = action.name }
    }

    suspend fun setContactMatchingEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_CONTACT_MATCHING_ENABLED] = enabled }
    }

    private companion object {
        val KEY_DEFAULT_REGION = stringPreferencesKey("default_region")
        val KEY_UNKNOWN_NUMBER_ACTION = stringPreferencesKey("unknown_number_action")
        val KEY_CONTACT_MATCHING_ENABLED = booleanPreferencesKey("contact_matching_enabled")
    }
}
