package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.findEventOfType
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.instrumentation.fcm.PushNotificationBreadcrumb.NotificationType
import io.embrace.android.embracesdk.internal.instrumentation.fcm.fcmDataSource
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.semconv.EmbPushNotificationAttributes
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class PushNotificationFeatureTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    @Test
    fun `log push notification and data type`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    logPushNotification(NotificationType.NOTIFICATION_AND_DATA)
                }
            },
            assertAction = {
                val msg = getSingleSessionEnvelope()
                msg.assertNotification("notif-data")
            }
        )
    }

    @Test
    fun `log push data type`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    logPushNotification(NotificationType.DATA)
                }
            },
            assertAction = {
                val msg = getSingleSessionEnvelope()
                msg.assertNotification("data")
            }
        )
    }

    @Test
    fun `log push notification no data type`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    logPushNotification(NotificationType.NOTIFICATION)
                }
            },
            assertAction = {
                val msg = getSingleSessionEnvelope()
                msg.assertNotification("notif")
            }
        )
    }

    @Test
    fun `log push unknown type`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    logPushNotification(NotificationType.UNKNOWN)
                }
            },
            assertAction = {
                val msg = getSingleSessionEnvelope()
                msg.assertNotification("unknown")
            }
        )
    }

    @Test
    fun `log push notification with pii`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(fcmPiiCapture = true),
            ),
            testCaseAction = {
                recordSession {
                    logPushNotification(NotificationType.NOTIFICATION_AND_DATA)
                }
            },
            assertAction = {
                val payload = getSingleSessionEnvelope()
                val sessionPartSpan = payload.findSessionPartSpan()
                val event = sessionPartSpan.findEventOfType(EmbType.System.PushNotification)
                assertTrue(checkNotNull(event.timestampNanos) > 0)
                event.attributes?.assertMatches(mapOf(
                    EmbType.System.PushNotification.asPair(),
                    EmbPushNotificationAttributes.NOTIFICATION_TITLE to "title",
                    EmbPushNotificationAttributes.NOTIFICATION_TYPE to "notif-data",
                    EmbPushNotificationAttributes.NOTIFICATION_BODY to "body",
                    EmbPushNotificationAttributes.NOTIFICATION_ID to "id",
                    EmbPushNotificationAttributes.NOTIFICATION_FROM to "from",
                    EmbPushNotificationAttributes.NOTIFICATION_PRIORITY to 1,
                ))
            }
        )
    }

    private fun logPushNotification(type: NotificationType) {
        checkNotNull(fcmDataSource).logPushNotification("title", "body", "from", "id", 1, type)
    }

    private fun Envelope<SessionPartPayload>.assertNotification(type: String) {
        val sessionPartSpan = findSessionPartSpan()
        val event = sessionPartSpan.findEventOfType(EmbType.System.PushNotification)
        assertTrue(checkNotNull(event.timestampNanos) > 0)
        event.attributes?.assertMatches(mapOf(
            EmbType.System.PushNotification.asPair(),
            "notification.type" to type,
            "notification.id" to "id",
            "notification.priority" to 1,
        ))
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
