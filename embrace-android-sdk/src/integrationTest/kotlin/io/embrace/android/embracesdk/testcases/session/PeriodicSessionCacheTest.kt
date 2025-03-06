package io.embrace.android.embracesdk.testcases.session

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.spans.getSessionProperty
import io.embrace.android.embracesdk.testframework.FakeCacheStorageService
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Asserts that the session is periodically cached.
 */
@Config(sdk = [TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class PeriodicSessionCacheTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `session is periodically cached`() {
        var snapshot: Envelope<SessionPayload>? = null
        testRule.runTest(
            setupAction = {
                cacheStorageServiceProvider = ::FakeCacheStorageService
            },
            testCaseAction = {
                val cacheStorageService = getCacheStorageService()
                recordSession {
                    assertEquals(0, cacheStorageService.storedPayloads.size)
                    embrace.addSessionProperty("Test", "Test", true)
                    val dataSupplier = { cacheStorageService.storedPayloads }
                    snapshot = returnIfConditionMet(
                        waitTimeMs = 10000,
                        desiredValueSupplier = { cacheStorageService.getLastCachedSession() },
                        dataProvider = dataSupplier,
                        condition = { data ->
                            data.size > 0 && cacheStorageService.getLastCachedSession().findSpanSnapshotOfType(EmbType.Ux.Session)
                                .getSessionProperty("Test") != null
                        },
                        errorMessageSupplier = { "Timeout waiting for cached session" }
                    )
                    embrace.addSessionProperty("Test", "Passed", true)
                }
            },
            assertAction = {
                val endMessage = checkNotNull(snapshot)
                val span = endMessage.findSpanSnapshotOfType(EmbType.Ux.Session)
                assertEquals("Test", span.getSessionProperty("Test"))
                span.attributes?.assertMatches(
                    mapOf(
                        "emb.clean_exit" to false,
                        "emb.terminated" to true
                    )
                )
                val completedMessage = getSingleSessionEnvelope()
                val completedSpan = completedMessage.findSessionSpan()
                assertEquals("Passed", completedSpan.getSessionProperty("Test"))
                completedSpan.attributes?.assertMatches(
                    mapOf(
                        "emb.clean_exit" to true,
                        "emb.terminated" to false
                    )
                )
            }
        )
    }

    private fun FakeCacheStorageService.getLastCachedSession(): Envelope<SessionPayload> {
        return TestPlatformSerializer().fromJson(
            checkNotNull(loadPayloadAsStream(storedPayloads.keys.last())),
            checkNotNull(SupportedEnvelopeType.SESSION.serializedType)
        )
    }

    private fun getCacheStorageService(): FakeCacheStorageService {
        return testRule.bootstrapper.deliveryModule.cacheStorageService as FakeCacheStorageService
    }
}
