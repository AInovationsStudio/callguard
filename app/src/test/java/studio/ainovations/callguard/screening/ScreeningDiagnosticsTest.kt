package studio.ainovations.callguard.screening

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.ainovations.callguard.domain.RuleAction

class ScreeningDiagnosticsTest {
    @Test
    fun decisionLogContainsOnlyNonSensitiveScreeningMetadata() {
        val message = ScreeningDiagnostics.decision(
            loaded = true,
            ruleCount = 2,
            action = RuleAction.BLOCK,
            ruleId = "rule-1",
        )

        assertTrue(message.contains("loaded=true"))
        assertTrue(message.contains("ruleCount=2"))
        assertTrue(message.contains("action=BLOCK"))
        assertTrue(message.contains("ruleId=rule-1"))
        assertFalse(message.contains("15718881234"))
        assertFalse(message.contains("Alice"))
    }

    @Test
    fun failureLogContainsExceptionTypeButNotExceptionMessage() {
        val message = ScreeningDiagnostics.failure(
            IllegalStateException("private caller data"),
        )

        assertTrue(message.contains("IllegalStateException"))
        assertFalse(message.contains("private caller data"))
        assertFalse(message.contains("15718881234"))
    }
}
