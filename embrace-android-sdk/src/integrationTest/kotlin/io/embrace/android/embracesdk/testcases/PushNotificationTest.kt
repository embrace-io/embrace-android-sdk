package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeBreadcrumbBehavior
import io.embrace.android.embracesdk.fakes.fakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.findEventOfType
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSentSessionMessages
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.session.OtelSessionGatingTest
import org.junit.Assert
import org.junit.Before
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
            embrace.start(harness.fakeCoreModule.context)
            harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, true)
            }

            assertNotification("notif-data")
        }
    }


    @Test
    fun `log push data type`() {
        with(testRule) {
            embrace.start(harness.fakeCoreModule.context)
            harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, false, true)
            }

            assertNotification("data")
        }
    }


    @Test
    fun `log push notification no data type`() {
        with(testRule) {
            embrace.start(harness.fakeCoreModule.context)
            harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, false)
            }

            assertNotification("notif")
        }
    }


    @Test
    fun `log push unknown type`() {
        with(testRule) {
            embrace.start(harness.fakeCoreModule.context)
            harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, false, false)
            }

            assertNotification("unknown")
        }
    }

    private fun IntegrationTestRule.assertNotification(type: String) {
        val payload = harness.getSentSessionMessages().single()
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
            harness.fakeConfigService.breadcrumbBehavior = fakeBreadcrumbBehavior(
                localCfg = { SdkLocalConfig(captureFcmPiiData = true) }
            )
            embrace.start(harness.fakeCoreModule.context)
            harness.recordSession {
                embrace.logPushNotification("title", "body", "from", "id", 1, 2, true, true)
            }

            val payload = harness.getSentSessionMessages().single()
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