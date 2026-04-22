package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.semconv.EmbAndroidAttributes
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LegacyExceptionInfo
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.internal.utils.getSafeStackTrace
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.opentelemetry.kotlin.logging.model.SeverityNumber
import io.opentelemetry.kotlin.semconv.LogAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class JvmCrashFeatureTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private val testException = RuntimeException("Boom!")
    private val serializer = EmbraceSerializer()
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
                    embrace.addUserSessionProperty("foo", "bar", PropertyScope.PERMANENT)
                    simulateJvmUncaughtException(testException)
                }.actionTimeMs
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                assertEquals(0, session.data.spanSnapshots?.size)
                val crash = getSingleLogEnvelope().getLastLog().apply {
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
                val ba = getSingleSessionEnvelope(AppState.BACKGROUND)
                assertEquals(0, ba.data.spanSnapshots?.size)
                getSingleLogEnvelope().getLastLog().assertCrash(
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
                getSingleLogEnvelope().getLastLog().assertCrash(
                    crashIdFromSession = null,
                    crashTimeMs = crashTimeMs,
                )
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
                val log = getSingleLogEnvelope().getLastLog()
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

                val message = getSingleSessionEnvelope()
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

    private fun Envelope<SessionPartPayload>.getCrashedId(): String {
        val crashId = checkNotNull(getSessionSpan()?.attributes?.findAttributeValue(EmbSessionAttributes.EMB_CRASH_ID))
        assertFalse(crashId.isBlank())
        return crashId
    }

    private fun Log.assertCrash(
        crashIdFromSession: String?,
        state: String = "background",
        crashTimeMs: Long,
        hasSession: Boolean = true,
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
            hasSession = hasSession,
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
