package studio.ainovations.callguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Project smoke test: confirms the build produced the expected namespace,
 * generated BuildConfig, and compiled the launcher activity. Runs on the
 * JVM as part of `./scripts/container-test.sh`. Fails fast if the project
 * skeleton is missing or misconfigured.
 */
class ProjectSmokeTest {

    @Test
    fun applicationNamespaceIsCallguard() {
        val pkg = BuildConfig::class.java.`package`?.name
        assertEquals("studio.ainovations.callguard", pkg)
    }

    @Test
    fun applicationIdMatchesNamespace() {
        assertEquals("studio.ainovations.callguard", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun launcherActivityCompiles() {
        val clazz = Class.forName("studio.ainovations.callguard.MainActivity")
        assertNotNull(clazz)
        assertEquals("studio.ainovations.callguard", clazz.`package`?.name)
    }
}
