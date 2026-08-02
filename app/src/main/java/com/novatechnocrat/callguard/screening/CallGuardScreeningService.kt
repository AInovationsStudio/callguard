package studio.ainovations.callguard.screening

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse
import studio.ainovations.callguard.data.CallGuardDatabase
import studio.ainovations.callguard.data.CallGuardPreferences
import studio.ainovations.callguard.data.PreferencesRepository
import studio.ainovations.callguard.data.RuleRepository
import studio.ainovations.callguard.data.callGuardDataStore
import studio.ainovations.callguard.domain.MatchResult
import studio.ainovations.callguard.domain.RuleAction
import studio.ainovations.callguard.phone.PhoneNormalizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class CallGuardScreeningService : CallScreeningService() {
    private lateinit var ruleRepository: RuleRepository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var decisionResolver: ScreeningDecisionResolver
    private lateinit var contactNumberProvider: ContactNumberProvider

    override fun onCreate() {
        super.onCreate()
        val context = applicationContext
        val normalizer = PhoneNormalizer(deviceRegion = { deviceRegionFor(context) })
        ruleRepository = RuleRepository(CallGuardDatabase.build(context).ruleDao())
        preferencesRepository = PreferencesRepository(context.callGuardDataStore)
        decisionResolver = ScreeningDecisionResolver(normalizer)
        contactNumberProvider = AndroidContactNumberProvider(
            contentResolver = contentResolver,
            normalizer = normalizer,
            region = deviceRegionFor(context),
        )
        runBlocking { ruleRepository.refreshFromDisk() }
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val result = runCatching {
            val preferences = runBlocking { preferencesRepository.preferences.first() }
            val contacts = if (preferences.contactMatchingEnabled) {
                runCatching { contactNumberProvider.loadCanonicalNumbers() }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
            decisionResolver.resolve(
                snapshot = ruleRepository.compileSnapshot(),
                rawNumber = callDetails.handle?.schemeSpecificPart,
                region = preferences.defaultRegion,
                preferences = preferences,
                contacts = contacts,
            )
        }.getOrElse {
            MatchResult(
                action = CallGuardPreferences.DEFAULT_UNKNOWN_NUMBER_ACTION,
                ruleId = null,
                explanation = "Screening failed safely; the default allow action was applied.",
            )
        }
        respondToCall(callDetails, result.toCallResponse())
    }

    private fun MatchResult.toCallResponse(): CallResponse =
        CallResponse.Builder().apply {
            when (action) {
                RuleAction.BLOCK -> {
                    setDisallowCall(true)
                    setRejectCall(true)
                    setSkipCallLog(true)
                    setSkipNotification(true)
                }
                RuleAction.SILENCE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setSilenceCall(true)
                }
                RuleAction.ALLOW -> Unit
            }
        }.build()

    private fun deviceRegionFor(context: android.content.Context): String? {
        val telephony = context.getSystemService(android.content.Context.TELEPHONY_SERVICE)
            as? android.telephony.TelephonyManager
        val simRegion = telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
        if (simRegion != null) return simRegion.uppercase()
        return context.resources.configuration.locales.get(0)?.country
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()
    }
}
