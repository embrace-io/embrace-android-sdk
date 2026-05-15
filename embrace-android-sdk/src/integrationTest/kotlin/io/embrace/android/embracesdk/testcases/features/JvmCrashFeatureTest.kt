package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.semconv.EmbAndroidAttributes
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LegacyExceptionInfo
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.internal.utils.getSafeStackTrace
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.kotlin.semconv.LogAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.GZIPInputStream

@RunWith(AndroidJUnit4::class)
internal class JvmCrashFeatureTest {

    private lateinit var payloadStorageService: FakePayloadStorageService

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(fakeStorageLayer = true).apply {
            getEmbLogger().throwOnInternalError = false
        }.also {
            payloadStorageService = checkNotNull(it.fakePayloadStorageService)
        }
    }

    private val testException = RuntimeException("Boom!")
    private val serializer = EmbraceSerializer()
    private val testSerializer = TestPlatformSerializer()
    private var crashTimeMs = 0L

    @Before
    fun before() {
        crashTimeMs = 0L
    }

    @Test
    fun `app crash generates an OTel Log and matches the crashId in the session`() {
        testRule.runTest(
            testCaseAction = {
                crashTimeMs = recordSession {
                    embrace.addSessionProperty("foo", "bar", true)
                    simulateJvmUncaughtException(testException)
                }.actionTimeMs
            },
            assertAction = {
                val session = payloadStorageService.getPersistedSession()
                assertEquals(0, session.data.spanSnapshots?.size)
                val crash = payloadStorageService.getPersistedCrashLog().getLastLog().apply {
                    assertCrash(
                        state = "foreground",
                        crashIdFromSession = session.getCrashedId(),
                        crashTimeMs = crashTimeMs,
                    )
                }

                assertEquals("bar", crash.attributes?.single { it.key == "foo".toEmbraceAttributeName() }?.data)
            }
        )
    }

    @Test
    fun `app crash in the background generates a crash log`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)),
            testCaseAction = {
                crashTimeMs = clock.now()
                simulateJvmUncaughtException(testException)
            },
            assertAction = {
                val ba = payloadStorageService.getPersistedSession(AppState.BACKGROUND)
                assertEquals(0, ba.data.spanSnapshots?.size)
                payloadStorageService.getPersistedCrashLog().getLastLog().assertCrash(
                    crashIdFromSession = ba.getCrashedId(),
                    crashTimeMs = crashTimeMs,
                )
            }
        )
    }

    @Test
    fun `app crash in the background generates a crash log even if background activity disabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(0f)),
            testCaseAction = {
                crashTimeMs = clock.now()
                simulateJvmUncaughtException(testException)
            },
            assertAction = {
                payloadStorageService.getPersistedCrashLog().getLastLog().assertCrash(
                    crashIdFromSession = null,
                    crashTimeMs = crashTimeMs,
                )
            }
        )
    }

    @Test
    fun `JVM crash is persisted exactly once and is not delivered during teardown`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    simulateJvmUncaughtException(testException)
                }
            },
            assertAction = {
                val crashEnvelopes = payloadStorageService.storedPayloadMetadata().filter {
                    it.payloadType == PayloadType.JVM_CRASH
                }
                assertEquals(1, crashEnvelopes.size)
                assertTrue(crashEnvelopes.single().complete)
                assertEquals(0, getLogEnvelopes(0).size)
            }
        )
    }

    @Test
    fun `React Native crash generates an OTel Log and matches the crashId in the session`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                project = FakeProjectConfig(
                    appId = "abcde",
                    appFramework = "react_native"
                )
            ),
            testCaseAction = {
                crashTimeMs = recordSession {
                    EmbraceInternalApi.reactNativeInternalInterface.logUnhandledJsException(
                        "name",
                        "message",
                        "type",
                        "stacktrace"
                    )
                    val handler = checkNotNull(Thread.getDefaultUncaughtExceptionHandler())
                    handler.uncaughtException(Thread.currentThread(), testException)
                }.actionTimeMs
            },
            assertAction = {
                val log = payloadStorageService.getPersistedCrashLog(PayloadType.REACT_NATIVE_CRASH).getLastLog()
                assertOtelLogReceived(
                    logReceived = log,
                    expectedMessage = "",
                    expectedSeverityNumber = SeverityNumber.ERROR,
                    expectedTimeMs = crashTimeMs,
                    expectedExceptionName = testException.javaClass.canonicalName,
                    expectedExceptionMessage = checkNotNull(testException.message),
                    expectedStacktrace = testException.getSafeStackTrace()?.toList(),
                    expectedProperties = emptyMap(),
                    expectedEmbType = "sys.android.react_native_crash",
                    expectedState = "foreground"
                )
                val exceptionInfo = LegacyExceptionInfo.ofThrowable(testException)
                val expectedExceptionCause = serializer.toJson(listOf(exceptionInfo), List::class.java)
                val expectedJsException = "{\"n\":\"name\",\"m\":\"message\",\"t\":\"type\",\"st\":\"stacktrace\"}"

                val message = payloadStorageService.getPersistedSession()
                val crashId = message.getSessionSpan()?.attributes?.findAttributeValue(EmbSessionAttributes.EMB_CRASH_ID)
                assertNotNull(crashId)
                log.attributes?.assertMatches(
                    mapOf(
                        EmbType.System.ReactNativeCrash.embAndroidReactNativeCrashJsException to expectedJsException,
                        EmbAndroidAttributes.EMB_ANDROID_CRASH_NUMBER to 1,
                        EmbType.System.Crash.embAndroidCrashExceptionCause to expectedExceptionCause,
                        LogAttributes.LOG_RECORD_UID to crashId
                    )
                )
                assertNotNull(log.attributes?.findAttributeValue(EmbAndroidAttributes.EMB_ANDROID_THREADS))
            }
        )
    }

    private fun FakePayloadStorageService.getPersistedSession(
        state: AppState = AppState.FOREGROUND,
    ): Envelope<SessionPartPayload> {
        val expectedState = state.name.lowercase()
        return storedPayloadMetadata()
            .filter { it.payloadType == PayloadType.SESSION && it.complete }
            .map { metadata ->
                testSerializer.fromJson<Envelope<SessionPartPayload>>(
                    GZIPInputStream(loadPayloadAsStream(metadata)),
                    checkNotNull(SupportedEnvelopeType.SESSION.serializedType)
                )
            }
            .single { envelope ->
                val attrs = envelope.getSessionSpan()?.attributes
                attrs?.findAttributeValue(EmbSessionAttributes.EMB_STATE) == expectedState &&
                    attrs.findAttributeValue(EmbSessionAttributes.EMB_CRASH_ID) != null
            }
    }

    private fun Envelope<SessionPartPayload>.getCrashedId(): String {
        val crashId = checkNotNull(getSessionSpan()?.attributes?.findAttributeValue(EmbSessionAttributes.EMB_CRASH_ID))
        assertFalse(crashId.isBlank())
        return crashId
    }

    private fun Log.assertCrash(
        crashIdFromSession: String?,
        state: String = "background",
        crashTimeMs: Long,
    ) {
        assertOtelLogReceived(
            logReceived = this,
            expectedMessage = "",
            expectedSeverityNumber = SeverityNumber.ERROR,
            expectedTimeMs = crashTimeMs,
            expectedExceptionName = testException.javaClass.canonicalName,
            expectedExceptionMessage = checkNotNull(testException.message),
            expectedStacktrace = testException.getSafeStackTrace()?.toList(),
            expectedProperties = emptyMap(),
            expectedEmbType = "sys.android.crash",
            expectedState = state,
            hasSession = crashIdFromSession != null,
        )

        val exceptionInfo = LegacyExceptionInfo.ofThrowable(testException)
        val expectedExceptionCause = serializer.toJson(listOf(exceptionInfo), List::class.java)

        attributes?.assertMatches(
            mapOf(
                EmbSessionAttributes.EMB_STATE to state,
                EmbAndroidAttributes.EMB_ANDROID_CRASH_NUMBER to 1,
                EmbType.System.Crash.embAndroidCrashExceptionCause to expectedExceptionCause,
            )
        )

        val foundCrashId = attributes?.findAttributeValue(LogAttributes.LOG_RECORD_UID)
        if (crashIdFromSession != null) {
            assertEquals(crashIdFromSession, foundCrashId)
        } else {
            assertNotNull(foundCrashId)
        }
        assertNotNull(attributes?.findAttributeValue(EmbAndroidAttributes.EMB_ANDROID_THREADS))
    }
}
