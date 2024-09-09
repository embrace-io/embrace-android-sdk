package io.embrace.android.embracesdk.internal.capture.crumbs

import android.os.Bundle
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PushNotificationCaptureServiceTest {

    private lateinit var pushNotificationCaptureService: PushNotificationCaptureService

    companion object {
        private val mockBundle: Bundle = mockk(relaxed = true)
    }

    @Before
    fun before() {
        clearAllMocks(answers = false)
        // for bundle let's clear answers too
        clearMocks(mockBundle)

        pushNotificationCaptureService = PushNotificationCaptureService(
            mockk(relaxed = true)
        )
    }

    @Test
    fun `verify get message priority`() {
        // if null return 0 (unknown)
        assertEquals(0, PushNotificationCaptureService.getMessagePriority(null))

        // if high return 1 (high)
        assertEquals(1, PushNotificationCaptureService.getMessagePriority("high"))

        // if normal return 2 (normal)
        assertEquals(2, PushNotificationCaptureService.getMessagePriority("normal"))

        // if any other thing return 0 (unknown)
        assertEquals(0, PushNotificationCaptureService.getMessagePriority("whatever"))
    }

    @Test
    fun `verify extract user defined data from bundle`() {
        // if empty bundle it should return empty map
        every { mockBundle.keySet() } returns emptySet()
        assertTrue(
            PushNotificationCaptureService.extractDeveloperDefinedPayload(mockBundle).isEmpty()
        )

        // if only reserved words it should return empty map
        every { mockBundle.keySet() } returns setOf(
            "google.key",
            "gcm.key",
            "from",
            "message_type",
            "collapse_key"
        )
        assertTrue(
            PushNotificationCaptureService.extractDeveloperDefinedPayload(mockBundle).isEmpty()
        )

        // reset mockBundle
        clearMocks(mockBundle)

        // if reserved words plus user defined keys it should return user defined keys only
        every { mockBundle.keySet() } returns setOf(
            "google.key",
            "gcm.key",
            "from",
            "message_type",
            "collapse_key",
            "user_defined_key1",
            "user_defined_key2"
        )
        every { mockBundle.getString("user_defined_key1") } returns "value1"
        every { mockBundle.getString("user_defined_key2") } returns "value2"
        val userDefinedMap =
            PushNotificationCaptureService.extractDeveloperDefinedPayload(mockBundle)

        assertEquals(2, userDefinedMap.size)
        assertEquals("value1", userDefinedMap["user_defined_key1"])
        assertEquals("value2", userDefinedMap["user_defined_key2"])
    }
}
