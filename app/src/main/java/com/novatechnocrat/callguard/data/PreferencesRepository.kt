package studio.ainovations.callguard.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import studio.ainovations.callguard.domain.RuleAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
 * number, and whether contact matching is enabled. The MVP does not persist
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

/**
 * Provides an Android Keystore-backed AES-256-GCM key for encrypting any
 * minimal screening-history metadata CallGuard may retain in the future.
 *
 * **Unused today.** The MVP does not implement screening history: no event
 * is ever recorded, and [PreferencesRepository] stores none. Per the design
 * spec ("the app does not store... unnecessary call history... any retained
 * sensitive metadata is encrypted using a key protected by Android
 * Keystore"), this object exists so a future task that enables minimal
 * history does not invent its own key handling — it is dormant scaffolding,
 * not a wired feature.
 *
 * If a future task enables minimal screening history, the ONLY fields it
 * may persist are documented here, and nowhere else:
 * 1. `timestamp: Long` — event epoch millis.
 * 2. `action: RuleAction` — the resolved action (ALLOW, BLOCK, or SILENCE).
 * 3. `ruleId: String?` — the matched rule id, or null for a default action.
 *
 * Explicitly forbidden, per the design spec's "no call audio, message
 * content, contacts export, or raw phone history": the caller's raw or
 * canonical phone number, contact name or identity, call duration, and any
 * audio or message content. A future implementation must not widen this
 * field list without updating this documentation.
 *
 * Not unit-testable outside a real Android Keystore provider (the "AndroidKeyStore"
 * provider does not exist on a bare JVM); a future `androidTest` should
 * validate key generation and an encrypt/decrypt round trip once real
 * metadata is added.
 */
object RetainedMetadataCipher {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "callguard_retained_metadata_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    /**
     * Returns the Keystore-backed [SecretKey], generating it on first use.
     * The key is non-exportable: [KeyGenParameterSpec] gives no way to read
     * back raw key material, and the Keystore isolates it from the app's
     * own process memory/storage.
     */
    fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /** A [Cipher] ready to encrypt; read [Cipher.getIV] to store alongside the ciphertext. */
    fun newEncryptCipher(): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }

    /** A [Cipher] ready to decrypt data encrypted with the [iv] produced by [newEncryptCipher]. */
    fun newDecryptCipher(iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
}
