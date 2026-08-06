package studio.ainovations.callguard.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallGuardRepositoryProviderTest {
    @Test
    fun rulesRepositoryIsSharedWithinTheApplicationProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertSame(
            CallGuardRepositoryProvider.rules(context),
            CallGuardRepositoryProvider.rules(context),
        )
    }
}
