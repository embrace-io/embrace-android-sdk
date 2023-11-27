package io.embrace.android.embracesdk.capture.crumbs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PushNotificationCaptureServiceTest {

    private lateinit var pushNotificationCaptureService: PushNotificationCaptureService

    companion object {
        private val mockBreadCrumbService: BreadcrumbService = mockk(relaxUnitFun = true)
        private val logger: InternalEmbraceLogger = InternalEmbraceLogger()
        private val mockBundle: Bundle = mockk()
        private val mockIntent: Intent = mockk {
            every { extras } returns mockBundle
        }
        private val mockActivity: Activity = mockk {
            every { intent } returns mockIntent
        }
    }

    @Before
    fun before() {
        clearAllMocks(answers = false)
        // for bundle let's clear answers too
        clearMocks(mockBundle)

        pushNotificationCaptureService = PushNotificationCaptureService(
            mockBreadCrumbService,
            logger
        )
    }

    @Test
    fun `verify log push notification successfully`() {
        val title = "title"
        val body = "body"
        val from = "from"
        val id = "id"
        val notificationPriority = 1
        val messageDeliveredPriority = 1
        val type = PushNotificationBreadcrumb.NotificationType.NOTIFICATION

        pushNotificationCaptureService.logPushNotification(
            title,
            body,
            from,
            id,
            notificationPriority,
            messageDeliveredPriority,
            type
        )

        verify {
            mockBreadCrumbService.logPushNotification(
                title,
                body,
                from,
                id,
                notificationPriority,
                messageDeliveredPriority,
                type
            )
        }
    }

    @Test
    fun `verify onActivityCreated for a push notification intent with no user data`() {
        // these are needed so to identify this is a push notification
        val fromKey = "from"
        val messageIdKey = "google.message_id"
        val deliveredPriorityKey = "google.delivered_priority"

        val bundleData = mapOf(
            fromKey to "123",
            messageIdKey to "456",
            deliveredPriorityKey to "normal"
        )
        every { mockBundle.keySet() } returns bundleData.keys
        every { mockBundle.getString(fromKey) } returns bundleData[fromKey]
        every { mockBundle.getString(messageIdKey) } returns bundleData[messageIdKey]
        every { mockBundle.getString(deliveredPriorityKey) } returns bundleData[deliveredPriorityKey]

        pushNotificationCaptureService.onActivityCreated(mockActivity, mockBundle)

        verify {
            mockBreadCrumbService.logPushNotification(
                null,
                null,
                "123",
                "456",
                null,
                // 2 is normal priority
                2,
                PushNotificationBreadcrumb.NotificationType.NOTIFICATION
            )
        }
    }

    @Test
    fun `verify onActivityCreated for a push notification intent with user data`() {
        // these are needed so to identify this is a push notification
        val fromKey = "from"
        val messageIdKey = "google.message_id"
        val deliveredPriorityKey = "google.delivered_priority"
        val userDefinedKey = "user-defined"

        val bundleData = mapOf(
            fromKey to "123",
            messageIdKey to "456",
            deliveredPriorityKey to "normal",
            userDefinedKey to "custom-value"
        )
        every { mockBundle.keySet() } returns bundleData.keys
        every { mockBundle.getString(fromKey) } returns bundleData[fromKey]
        every { mockBundle.getString(messageIdKey) } returns bundleData[messageIdKey]
        every { mockBundle.getString(deliveredPriorityKey) } returns bundleData[deliveredPriorityKey]
        every { mockBundle.getString(userDefinedKey) } returns bundleData[userDefinedKey]

        pushNotificationCaptureService.onActivityCreated(mockActivity, mockBundle)

        verify {
            mockBreadCrumbService.logPushNotification(
                null,
                null,
                "123",
                "456",
                null,
                // 2 is normal priority
                2,
                PushNotificationBreadcrumb.NotificationType.NOTIFICATION_AND_DATA
            )
        }
    }

    @Test
    fun `verify onActivityCreated that is not coming from push notification`() {
        // empty key set so this is not a push notification intent
        every { mockBundle.keySet() } returns emptySet()

        pushNotificationCaptureService.onActivityCreated(mockActivity, mockBundle)

        verify { mockBreadCrumbService wasNot Called }
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
            "google.key", "gcm.key", "from", "message_type", "collapse_key"
        )
        assertTrue(
            PushNotificationCaptureService.extractDeveloperDefinedPayload(mockBundle).isEmpty()
        )

        // reset mockBundle
        clearMocks(mockBundle)

        // if reserved words plus user defined keys it should return user defined keys only
        every { mockBundle.keySet() } returns setOf(
            "google.key", "gcm.key", "from", "message_type", "collapse_key", "user_defined_key1",
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
