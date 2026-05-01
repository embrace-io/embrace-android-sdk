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
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule.Companion.DEFAULT_SDK_START_TIME_MS
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertNotNull
import org.junit.Ignore
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

    @Ignore("Bug: user session scoped properties don't persist across app instances")
    @Test
    fun `user-session-scoped property does not survive an expired user-session boundary across processes`() {
        val oldUserSessionId = USER_SESSION_ID_A
        val sessionStartMs = DEFAULT_SDK_START_TIME_MS - DEFAULT_INACTIVITY_TIMEOUT_MS - 60_000L

        testRule.runTest(
            setupAction = {
                persistUserSession(
                    sessionId = oldUserSessionId,
                    startMs = sessionStartMs,
                    lastActivityMs = DEFAULT_SDK_START_TIME_MS - DEFAULT_INACTIVITY_TIMEOUT_MS - 1L,
                )
                // TODO: add persisted user session property
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                // TODO: assert user session property
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
        const val USER_SESSION_ID_A = "aabbccdd11223344aabbccdd11223344"
        const val DEFAULT_INACTIVITY_TIMEOUT_MS = 1_800L * 1_000L
    }
}
