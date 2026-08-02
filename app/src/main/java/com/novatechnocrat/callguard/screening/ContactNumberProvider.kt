package studio.ainovations.callguard.screening

import android.content.ContentResolver
import android.provider.ContactsContract
import studio.ainovations.callguard.phone.PhoneNormalizationResult
import studio.ainovations.callguard.phone.PhoneNormalizer
import studio.ainovations.callguard.phone.PhoneNumberInput

interface ContactNumberProvider {
    fun loadCanonicalNumbers(): Set<String>
}

class AndroidContactNumberProvider(
    private val contentResolver: ContentResolver,
    private val normalizer: PhoneNormalizer,
    private val region: String?,
) : ContactNumberProvider {
    override fun loadCanonicalNumbers(): Set<String> {
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
