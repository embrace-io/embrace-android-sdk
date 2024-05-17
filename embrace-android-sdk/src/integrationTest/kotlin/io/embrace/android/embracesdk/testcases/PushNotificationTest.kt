package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.fakes.fakeBreadcrumbBehavior
import io.embrace.android.embracesdk.findEventOfType
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class PushNotificationTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule(
        harnessSupplier = {
            IntegrationTestRule.newHarness(startImmediately = false)
        }
    )

    @Test
    fun `log push notification and data type`() {
        with(testRule) {
            embrace.start(harness.overriddenCoreModule.context)
            harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, true)
            }

            assertNotification("notif-data")
        }
    }


    @Test
    fun `log push data type`() {
        with(testRule) {
            embrace.start(harness.overriddenCoreModule.context)
            harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, false, true)
            }

            assertNotification("data")
        }
    }


    @Test
    fun `log push notification no data type`() {
        with(testRule) {
            embrace.start(harness.overriddenCoreModule.context)
            harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, false)
            }

            assertNotification("notif")
        }
    }


    @Test
    fun `log push unknown type`() {
        with(testRule) {
            embrace.start(harness.overriddenCoreModule.context)
            harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, false, false)
            }

            assertNotification("unknown")
        }
    }

    private fun IntegrationTestRule.assertNotification(type: String) {
        val payload = harness.getSentSessions().single()
        val sessionSpan = payload.findSessionSpan()
        Assert.assertNotNull(sessionSpan)
        val event = sessionSpan.findEventOfType(EmbType.System.PushNotification)
        Assert.assertNotNull(event)
        Assert.assertTrue(event.timestampNanos > 0)
        Assert.assertEquals(
            mapOf(
                EmbType.System.PushNotification.toEmbraceKeyValuePair(),
                "notification.title" to "",
                "notification.type" to type,
                "notification.body" to "",
                "notification.id" to "id",
                "notification.from" to "",
                "notification.priority" to "1"
            ),
            event.attributes
        )
    }


    @Test
    fun `log push notification with pii`() {
        with(testRule) {
            harness.overriddenConfigService.breadcrumbBehavior = fakeBreadcrumbBehavior(
                localCfg = { SdkLocalConfig(captureFcmPiiData = true) }
            )
            startSdk()
            harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, true)
            }

            val payload = harness.getSentSessions().single()
            val sessionSpan = payload.findSessionSpan()
            Assert.assertNotNull(sessionSpan)
            val event = sessionSpan.findEventOfType(EmbType.System.PushNotification)
            Assert.assertNotNull(event)
            Assert.assertTrue(event.timestampNanos > 0)
            Assert.assertEquals(
                mapOf(
                    EmbType.System.PushNotification.toEmbraceKeyValuePair(),
                    "notification.title" to "title",
                    "notification.type" to "notif-data",
                    "notification.body" to "body",
                    "notification.id" to "id",
                    "notification.from" to "from",
                    "notification.priority" to "1"
                ),
                event.attributes
            )
        }
    }
}