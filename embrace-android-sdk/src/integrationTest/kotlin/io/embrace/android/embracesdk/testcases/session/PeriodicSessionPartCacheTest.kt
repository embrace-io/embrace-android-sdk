package io.embrace.android.embracesdk.testcases.session

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.internal.session.getSessionProperty
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.zip.GZIPInputStream

/**
 * Asserts that the session is periodically cached.
 */
@Config(sdk = [TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class PeriodicPartCacheTest {

    private lateinit var cacheStorageService: FakePayloadStorageService

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(fakeStorageLayer = true).also {
            cacheStorageService = checkNotNull(it.fakeCacheStorageService)
        }
    }

    @Test
    fun `session is periodically cached`() {
        var snapshot: Envelope<SessionPartPayload>? = null
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.addUserSessionProperty("Test", "Test", PropertyScope.PERMANENT)
                    snapshot = returnIfConditionMet(
                        waitTimeMs = 10000,
                        desiredValueSupplier = { cacheStorageService.getLastCachedPart() },
                        dataProvider = { cacheStorageService.getLastCachedPart() },
                        condition = { data ->
                            data != null && data.findSpanSnapshotOfType(EmbType.Ux.Session).getSessionProperty("Test") != null
                        },
                        errorMessageSupplier = { "Timeout waiting for cached session" }
                    )
                    embrace.addUserSessionProperty("Test", "Passed", PropertyScope.PERMANENT)
                }
            },
            assertAction = {
                val endMessage = checkNotNull(snapshot)
                val span = endMessage.findSpanSnapshotOfType(EmbType.Ux.Session)
                assertEquals("Test", span.getSessionProperty("Test"))
                span.attributes?.assertMatches(
                    mapOf(
                        EmbSessionAttributes.EMB_CLEAN_EXIT to false,
                        EmbSessionAttributes.EMB_TERMINATED to true
                    )
                )
                val completedMessage = getSingleSessionEnvelope()
                val completedSpan = completedMessage.findSessionSpan()
                assertEquals("Passed", completedSpan.getSessionProperty("Test"))
                completedSpan.attributes?.assertMatches(
                    mapOf(
                        EmbSessionAttributes.EMB_CLEAN_EXIT to true,
                        EmbSessionAttributes.EMB_TERMINATED to false
                    )
                )
            }
        )
    }

    private fun FakePayloadStorageService.getLastCachedPart(): Envelope<SessionPartPayload>? =
        storedPayloadMetadata()
            .filter { it.payloadType == PayloadType.SESSION }
            .let { sessions ->
                return if (sessions.isEmpty()) {
                    null
                } else {
                    val lastSessionMetadata = sessions.last()
                    return TestPlatformSerializer().fromJson(
                        GZIPInputStream(loadPayloadAsStream(lastSessionMetadata)),
                        checkNotNull(SupportedEnvelopeType.SESSION.serializedType)
                    )
                }
            }
}
