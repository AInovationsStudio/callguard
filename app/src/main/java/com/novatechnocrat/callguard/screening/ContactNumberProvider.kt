package studio.ainovations.callguard.screening

import android.content.ContentResolver
import android.provider.ContactsContract
import studio.ainovations.callguard.phone.PhoneNormalizationResult
import studio.ainovations.callguard.phone.PhoneNormalizer
import studio.ainovations.callguard.phone.PhoneNumberInput
import java.util.concurrent.atomic.AtomicReference

interface ContactNumberProvider {
    fun loadCanonicalNumbers(region: String?): Set<String>
}

class AndroidContactNumberProvider(
    private val contentResolver: ContentResolver,
    private val normalizer: PhoneNormalizer,
) : ContactNumberProvider {
    override fun loadCanonicalNumbers(region: String?): Set<String> {
        val canonicalNumbers = mutableSetOf<String>()
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (numberIndex < 0) return@use
            while (cursor.moveToNext()) {
                val rawNumber = cursor.getString(numberIndex) ?: continue
                val normalized = normalizer.normalize(PhoneNumberInput(rawNumber, region))
                if (normalized is PhoneNormalizationResult.Valid) {
                    canonicalNumbers += normalized.phone.digits
                }
            }
        }
        return canonicalNumbers
    }
}

data class ContactCacheSnapshot(
    val available: Boolean = false,
    val region: String? = null,
    val numbers: Set<String> = emptySet(),
)

/**
 * Keeps contact normalization off the call-screening callback path.
 *
 * A failed refresh clears the previous set rather than retaining stale contact
 * membership after permission is revoked or the selected region changes.
 */
class ContactNumberCache(
    private val provider: ContactNumberProvider,
) {
    private val snapshot = AtomicReference(ContactCacheSnapshot())

    fun current(): ContactCacheSnapshot = snapshot.get()

    suspend fun refresh(region: String?) {
        val refreshed = runCatching {
            ContactCacheSnapshot(
                available = true,
                region = region,
                numbers = provider.loadCanonicalNumbers(region),
            )
        }.getOrElse {
            ContactCacheSnapshot(available = false, region = region)
        }
        snapshot.set(refreshed)
    }
}
