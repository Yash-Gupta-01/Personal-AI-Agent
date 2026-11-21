package com.aether.domain.privacy

import com.aether.domain.decision.CloudDecisionPort
import com.aether.domain.decision.DecisionAnswer
import com.aether.domain.decision.DecisionQuestion
import com.aether.domain.decision.DecisionResult
import com.aether.domain.decision.LocalDecisionPort
import com.aether.domain.decision.PrivacyGatedDecisionRouter
import com.aether.domain.memory.Memory
import com.aether.domain.memory.MemorySource
import com.aether.domain.memory.MemoryStatus
import com.aether.domain.memory.MemoryType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PrivacyBoundaryTest {

    private class MockLocalDecisionPort : LocalDecisionPort {
        var callCount: Int = 0
        var lastInput: LocalDecisionInput? = null

        override suspend fun evaluate(
            input: LocalDecisionInput,
            questions: Map<String, DecisionQuestion>,
        ): DecisionResult {
            callCount++
            lastInput = input
            return DecisionResult(answers = emptyMap(), latencyMs = 5L)
        }
    }

    private class MockCloudDecisionPort : CloudDecisionPort {
        var callCount: Int = 0
        var lastInput: CloudSafeDecisionInput? = null

        override suspend fun evaluate(
            input: CloudSafeDecisionInput,
            questions: Map<String, DecisionQuestion>,
        ): DecisionResult {
            callCount++
            lastInput = input
            return DecisionResult(answers = emptyMap(), latencyMs = 200L)
        }
    }

    private class PassthroughRedactor : PiiRedactor {
        override suspend fun redact(text: String): RedactionResult =
            RedactionResult(redactedText = text.replace("John Doe", "[PERSON_1]"), tokenToRealValue = mapOf("[PERSON_1]" to "John Doe"))

        override suspend fun rehydrate(redactedText: String, tokenToRealValue: Map<String, String>): String =
            redactedText.replace("[PERSON_1]", "John Doe")
    }

    private class FailingAmbiguousRedactor : PiiRedactor {
        override suspend fun redact(text: String): RedactionResult {
            throw RedactionException.AmbiguousEntityException("Entity boundary is ambiguous for token")
        }

        override suspend fun rehydrate(redactedText: String, tokenToRealValue: Map<String, String>): String = redactedText
    }

    private fun createMemory(content: String, localOnly: Boolean): Memory = Memory(
        id = "mem-123",
        content = content,
        type = MemoryType.FACT,
        source = MemorySource.MANUAL_TEXT,
        status = MemoryStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        localOnly = localOnly,
    )

    @Test
    fun `localOnly memory is rejected by CloudSafeDecisionInput factory with PrivacyInvariantViolationException`() = runTest {
        val memory = createMemory("Private medical note: fever 102", localOnly = true)
        val redactor = PassthroughRedactor()

        val result = CloudSafeDecisionInput.create(memory, redactor)

        assertTrue("Expected failure for localOnly memory", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(
            "Expected PrivacyInvariantViolationException, got ${exception?.javaClass?.simpleName}",
            exception is PrivacyInvariantViolationException
        )
    }

    @Test
    fun `router routes localOnly memory strictly to LocalDecisionPort with zero calls to CloudDecisionPort`() = runTest {
        val memory = createMemory("Confidential strategy: missed morning gym", localOnly = true)
        val localPort = MockLocalDecisionPort()
        val cloudPort = MockCloudDecisionPort()
        val redactor = PassthroughRedactor()

        val router = PrivacyGatedDecisionRouter(
            localPort = localPort,
            cloudPort = cloudPort,
            piiRedactor = redactor,
        )

        val result = router.evaluate(memory, emptyMap())

        assertTrue("Local evaluation should succeed", result.isSuccess)
        assertEquals("Local port must be called exactly once", 1, localPort.callCount)
        assertEquals("Cloud port must have ZERO calls", 0, cloudPort.callCount)
        assertEquals("Local port must receive raw unredacted text", "Confidential strategy: missed morning gym", localPort.lastInput?.rawText)
    }

    @Test
    fun `router fails closed on ambiguous or failing redaction with zero calls to CloudDecisionPort`() = runTest {
        val memory = createMemory("Meeting with John Doe at office", localOnly = false)
        val localPort = MockLocalDecisionPort()
        val cloudPort = MockCloudDecisionPort()
        val redactor = FailingAmbiguousRedactor()

        val router = PrivacyGatedDecisionRouter(
            localPort = localPort,
            cloudPort = cloudPort,
            piiRedactor = redactor,
        )

        val result = router.evaluate(memory, emptyMap())

        assertTrue("Router must fail closed when redaction fails", result.isFailure)
        assertTrue(
            "Exception should be AmbiguousEntityException",
            result.exceptionOrNull() is RedactionException.AmbiguousEntityException
        )
        assertEquals("Cloud port must have ZERO calls on redaction failure", 0, cloudPort.callCount)
        assertEquals("Local port should not be invoked when preferLocal is false and cloud was target", 0, localPort.callCount)
    }

    @Test
    fun `cloud eligible memory is redacted and passed exclusively to CloudDecisionPort as CloudSafeDecisionInput`() = runTest {
        val memory = createMemory("Meeting with John Doe about gym workout", localOnly = false)
        val localPort = MockLocalDecisionPort()
        val cloudPort = MockCloudDecisionPort()
        val redactor = PassthroughRedactor()

        val router = PrivacyGatedDecisionRouter(
            localPort = localPort,
            cloudPort = cloudPort,
            piiRedactor = redactor,
        )

        val result = router.evaluate(memory, emptyMap())

        assertTrue("Cloud evaluation should succeed", result.isSuccess)
        assertEquals("Local port must have 0 calls", 0, localPort.callCount)
        assertEquals("Cloud port must have exactly 1 call", 1, cloudPort.callCount)
        assertEquals("Cloud port must receive sanitized text", "Meeting with [PERSON_1] about gym workout", cloudPort.lastInput?.sanitizedText)
        assertEquals("CloudSafeDecisionInput retains source memory ID", "mem-123", cloudPort.lastInput?.sourceMemoryId)
    }

    @Test
    fun `redactStrict fails closed on malformed unclosed redaction tokens`() = runTest {
        val redactor = object : PiiRedactor {
            override suspend fun redact(text: String): RedactionResult =
                RedactionResult(redactedText = "Malformed [EMAIL_unclosed", tokenToRealValue = emptyMap())

            override suspend fun rehydrate(redactedText: String, tokenToRealValue: Map<String, String>): String = redactedText
        }

        val result = redactor.redactStrict("test malformed")

        assertTrue("redactStrict must fail closed on malformed brackets", result.isFailure)
        assertTrue(
            "Expected ParsingException, got ${result.exceptionOrNull()?.javaClass?.simpleName}",
            result.exceptionOrNull() is RedactionException.ParsingException
        )
    }
}
