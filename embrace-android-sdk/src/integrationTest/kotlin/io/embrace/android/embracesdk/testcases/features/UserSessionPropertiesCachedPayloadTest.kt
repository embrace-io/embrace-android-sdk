package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.getSessionProperty
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.GZIPInputStream

@RunWith(AndroidJUnit4::class)
internal class UserSessionPropertiesCachedPayloadTest {

    private lateinit var cacheStorageService: FakePayloadStorageService

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(fakeStorageLayer = true).also {
            cacheStorageService = checkNotNull(it.fakeCacheStorageService)
        }
    }

    @Test
    fun `session properties are persisted in cached payloads`() {
        var snapshot: Envelope<SessionPartPayload>? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(bgActivityCapture = true)),
            setupAction = {
                setupPermanentUserSessionProperties(mapOf(EXISTING_KEY to VALUE))
            },
            testCaseAction = {
                recordSession {
                    embrace.addUserSessionProperty(PERM_KEY, VALUE, PropertyScope.PERMANENT)
                    snapshot = returnIfConditionMet(
                        waitTimeMs = 10_000,
                        desiredValueSupplier = { cacheStorageService.getLastCachedPart() },
                        dataProvider = { cacheStorageService.getLastCachedPart() },
                        condition = {
                            it != null && it.findSpanSnapshotOfType(EmbType.Ux.Session).getSessionProperty(PERM_KEY) != null
                        },
                        errorMessageSupplier = { "Timeout waiting for cached session containing permanent property" },
                    )
                }
            },
            assertAction = {
                val span = checkNotNull(snapshot).findSpanSnapshotOfType(EmbType.Ux.Session)
                assertNotNull(span.getSessionProperty(EXISTING_KEY))
                assertNotNull(span.getSessionProperty(PERM_KEY))
            },
        )
    }

    @Test
    fun `session properties are persisted in cached payloads when bg activities are disabled`() {
        var snapshot: Envelope<SessionPartPayload>? = null
        testRule.runTest(
            setupAction = {
                setupPermanentUserSessionProperties(mapOf(EXISTING_KEY to VALUE))
            },
            testCaseAction = {
                recordSession {
                    embrace.addUserSessionProperty(PERM_KEY, VALUE, PropertyScope.PERMANENT)
                    snapshot = returnIfConditionMet(
                        waitTimeMs = 10_000,
                        desiredValueSupplier = { cacheStorageService.getLastCachedPart() },
                        dataProvider = { cacheStorageService.getLastCachedPart() },
                        condition = {
                            it != null && it.findSpanSnapshotOfType(EmbType.Ux.Session).getSessionProperty(PERM_KEY) != null
                        },
                        errorMessageSupplier = { "Timeout waiting for cached session containing permanent property (BG disabled)" },
                    )
                }
            },
            assertAction = {
                val span = checkNotNull(snapshot).findSpanSnapshotOfType(EmbType.Ux.Session)
                assertNotNull(span.getSessionProperty(EXISTING_KEY))
                assertNotNull(span.getSessionProperty(PERM_KEY))
            },
        )
    }

    private fun FakePayloadStorageService.getLastCachedPart(): Envelope<SessionPartPayload>? =
        storedPayloadMetadata()
            .filter { it.payloadType == PayloadType.SESSION }
            .let { sessions ->
                if (sessions.isEmpty()) {
                    null
                } else {
                    val lastSessionMetadata = sessions.last()
                    TestPlatformSerializer().fromJson(
                        GZIPInputStream(loadPayloadAsStream(lastSessionMetadata)),
                        checkNotNull(SupportedEnvelopeType.SESSION.serializedType),
                    )
                }
            }

    private companion object {
        const val EXISTING_KEY = "existing"
        const val PERM_KEY = "perm"
        const val VALUE = "value"
    }
}
