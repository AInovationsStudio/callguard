package studio.ainovations.callguard.screening

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallGuardScreeningServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun serviceIsExportedOnlyThroughThePlatformBindingPermission() {
        val component = ComponentName(context, CallGuardScreeningService::class.java)
        val info = context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)

        assertTrue(info.exported)
        assertEquals("android.permission.BIND_SCREENING_SERVICE", info.permission)
    }

    @Test
    fun serviceAdvertisesTheCallScreeningIntent() {
        val intent = Intent("android.telecom.CallScreeningService").setPackage(context.packageName)
        val matches = context.packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)

        assertTrue(matches.any { it.serviceInfo.name == CallGuardScreeningService::class.java.name })
    }
}
