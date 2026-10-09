@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.testcases

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.UserSessionListener
import io.embrace.android.embracesdk.assertions.findSpanByName
import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.NoopInternalInterfaceApi
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.instance.SdkState
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.NoopEmbraceSdkSpan
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.network.http.HttpRequestInfoModifier
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.tracing.data.SpanData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import java.io.File

/**
 * Covers every transition of the SDK lifecycle state machine.
 *
 * Calls that do not lead to a valid transition must leave the state untouched and never throw.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class SdkStateIntegrationTest(
    private val otelSdkMode: OtelSdkMode,
) {

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()

        private const val TEST_PREFIX = "emb_test_"
        private const val SPAN_1 = "${TEST_PREFIX}1"
        private const val SPAN_2 = "${TEST_PREFIX}2"
        private const val SPAN_3 = "${TEST_PREFIX}3"
        private const val LOG_1 = "${TEST_PREFIX}1"
        private const val LOG_2 = "${TEST_PREFIX}2"
        private const val LOG_3 = "${TEST_PREFIX}3"
        private const val COMPLETED_SPAN = "${TEST_PREFIX}completed"
        private const val RESOURCE_ATTR_KEY = "${TEST_PREFIX}resource"
        private const val RESOURCE_ATTR_VALUE = "value"
        private const val TEST_SUBDIR_NAME = "emb_test_dir"
        private const val TEST_FILE_NAME = "test_file"
        private const val DUMMY_CONTENT = "Hello, world!"
        private val STARTUP_SWEPT_DIRS = setOf(StorageLocation.SESSION_SPLIT)
        private val sdkDisabledConfig = RemoteConfig(threshold = 0)
        private fun File.sentinelFile(): File = File(File(this, TEST_SUBDIR_NAME), TEST_FILE_NAME)

        private fun assertNoInternalErrors(logger: FakeInternalLogger) {
            assertEquals(emptyList<String>(), logger.internalErrorMessages.map { it.msg })
        }
    }

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var embraceDirs: Map<StorageLocation, File>

    @Before
    fun setUp() {
        embraceDirs = StorageLocation.entries.associateWith {
            it.asFile(
                logger = FakeInternalLogger(),
                rootDirSupplier = { context.filesDir },
                fallbackDirSupplier = { context.cacheDir },
            ).value
        }
    }

    @Test
    fun `NOT_STARTED - API calls are safe, buffered calls are replayed`() {
        lateinit var logger: FakeInternalLogger
        lateinit var calls: ApiCalls
        testRule.runTest(
            startSdk = false,
            setupAction = {
                logger = getEmbLogger().apply {
                    throwOnInternalError = false
                }
            },
            testCaseAction = {
                calls = ApiCalls(clock.now())
                assertTrue(calls.callBufferedApis(embrace))
                calls.callDroppedApis(embrace)
                assertEquals(SdkState.NOT_STARTED, sdkState)

                embrace.start(context)
                assertEquals(SdkState.STARTED, sdkState)
                recordSession {
                    embrace.startSpan(SPAN_1).stop()
                    embrace.logInfo(LOG_2)
                }
            },
            assertAction = {
                calls.assertDropped(logger)

                val span = getSingleSessionEnvelope().findSpanByName(COMPLETED_SPAN)
                assertEquals(calls.startMs.millisToNanos(), span.startTimeNanos)
                assertEquals(calls.endMs.millisToNanos(), span.endTimeNanos)
                assertEquals("value", span.attributes?.findAttributeValue("key"))
                assertEquals(
                    calls.experimentRecords,
                    testRule.bootstrapper.essentialServiceModule.experimentTrackingService.getRecords(),
                )
                assertEquals(listOf(LOG_2), getSingleLogEnvelope().data.logs?.map { it.body })
            },
            otelExportAssertion = {
                val exportedSpan = awaitSpans(1) { it.name == SPAN_1 }.single()
                assertEquals(RESOURCE_ATTR_VALUE, exportedSpan.resource.attributes.toStringMap()[RESOURCE_ATTR_KEY])
                awaitLogs(1) { it.bodyValue?.value == LOG_2 }
                calls.assertOTelConfigApplied(SPAN_1, LOG_2)
            },
        )
    }

    @Test
    fun `NOT_STARTED - disable is ignored`() {
        testRule.runTest(
            startSdk = false,
            testCaseAction = {
                embrace.disable()
                assertEquals(SdkState.NOT_STARTED, sdkState)

                embrace.start(context)
                assertEquals(SdkState.STARTED, sdkState)
                recordSession {
                    embrace.logInfo(LOG_1)
                }
            },
            assertAction = {
                assertEquals(SdkState.STARTED, sdkState)
                getSingleSessionEnvelope()
                getSingleLogEnvelope()
            },
        )
    }

    @Test
    fun `NOT_STARTED to STARTED - successful start`() {
        testRule.runTest(
            testCaseAction = {
                assertEquals(SdkState.STARTED, sdkState)
                assertTrue(embrace.isStarted)
                recordSession {
                    assertNotNull(embrace.currentUserSessionId)
                    embrace.logInfo(LOG_1)
                }
            },
            assertAction = {
                getSingleSessionEnvelope()
                getSingleLogEnvelope()
            },
        )
    }

    @Test
    fun `NOT_STARTED to NOT_STARTED - start is rejected when remote config disables the SDK`() {
        lateinit var logger: FakeInternalLogger
        testRule.runTest(
            persistedRemoteConfig = sdkDisabledConfig,
            expectSdkToStart = false,
            setupAction = {
                logger = getEmbLogger().apply {
                    throwOnInternalError = false
                }
            },
            testCaseAction = {
                assertEquals(SdkState.NOT_STARTED, sdkState)
                assertFalse(embrace.isStarted)
                recordSession {
                    embrace.logInfo(LOG_1)
                }
            },
            assertAction = {
                assertNoInternalErrors(logger)
                assertEquals(listOf("log_message"), logger.sdkNotInitializedMessages.map { it.msg })
                assertEquals(0, getLogEnvelopes(0).size)
                assertEquals(0, getSessionEnvelopes(0).size)
            },
        )
    }

    @Test
    fun `STARTED - repeated start is ignored`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    val userSessionId = embrace.currentUserSessionId
                    embrace.start(context)
                    assertEquals(SdkState.STARTED, sdkState)
                    assertEquals(userSessionId, embrace.currentUserSessionId)
                }
            },
            assertAction = {
                assertConfigRequested(1)
                getSingleSessionEnvelope()
            },
        )
    }

    @Test
    fun `STARTED to DISABLED - data export stops`() {
        lateinit var logger: FakeInternalLogger
        testRule.runTest(
            setupAction = {
                logger = getEmbLogger().apply {
                    throwOnInternalError = false
                }
                // create dummy values in embrace directories to see if they are deleted
                embraceDirs.values.forEach {
                    it.sentinelFile().apply {
                        parentFile?.mkdirs()
                        writeText(DUMMY_CONTENT)
                    }
                }
            },
            testCaseAction = {
                embraceDirs.filterKeys { it !in STARTUP_SWEPT_DIRS }.values.forEach {
                    assertEquals(DUMMY_CONTENT, it.sentinelFile().readText())
                }
                recordSession {
                    embrace.startSpan(SPAN_1).stop()
                    embrace.logInfo(LOG_1)
                    embrace.startSpan(SPAN_2).stop()
                    embrace.logInfo(LOG_2)

                    embrace.disable()
                    assertEquals(SdkState.DISABLED, sdkState)
                    assertFalse(embrace.isStarted)

                    embrace.startSpan(SPAN_3).stop()
                    embrace.logInfo(LOG_3)
                }
            },
            assertAction = {
                assertNoInternalErrors(logger)
                awaitStorageDeleted()
                assertEquals(0, getLogEnvelopes(0).size)
                assertEquals(0, getSessionEnvelopes(0).size)
            },
            otelExportAssertion = {
                val spanData = awaitSpans(2) { spanData ->
                    spanData.name.startsWith(TEST_PREFIX)
                }
                assertEquals(listOf(SPAN_1, SPAN_2), spanData.map { it.name })

                val logData = awaitLogs(2) { logData ->
                    logData.bodyValue?.value.toString().startsWith(TEST_PREFIX)
                }
                assertEquals(listOf(LOG_1, LOG_2), logData.map { it.bodyValue?.value })
            },
        )
    }

    @Test
    fun `DISABLED - API calls are safe, buffered calls are dropped`() {
        lateinit var logger: FakeInternalLogger
        lateinit var calls: ApiCalls
        testRule.runTest(
            setupAction = {
                logger = getEmbLogger().apply {
                    throwOnInternalError = false
                }
            },
            testCaseAction = {
                embrace.disable()
                assertEquals(SdkState.DISABLED, sdkState)

                calls = ApiCalls(clock.now())
                assertTrue(calls.callBufferedApis(embrace))
                calls.callDroppedApis(embrace, noopSpans = false)
                assertEquals(SdkState.DISABLED, sdkState)
                recordSession()
            },
            assertAction = {
                calls.assertDropped(logger, bufferedCallsDropped = true)
                calls.assertOTelConfigNotApplied()
                awaitStorageDeleted()
                assertEquals(0, getLogEnvelopes(0).size)
                assertEquals(0, getSessionEnvelopes(0).size)
            },
        )
    }

    @Test
    fun `DISABLED - repeated disable is ignored`() {
        testRule.runTest(
            testCaseAction = {
                embrace.disable()
                embrace.disable()
                assertEquals(SdkState.DISABLED, sdkState)
                assertFalse(embrace.isStarted)
            },
            assertAction = {
                awaitStorageDeleted()
            },
        )
    }

    @Test
    fun `DISABLED to STARTED - start restarts the SDK`() {
        lateinit var logger: FakeInternalLogger
        lateinit var calls: ApiCalls
        testRule.runTest(
            setupAction = {
                logger = getEmbLogger().apply {
                    throwOnInternalError = false
                }
            },
            testCaseAction = {
                embrace.disable()
                assertEquals(SdkState.DISABLED, sdkState)
                assertNull(embrace.currentUserSessionId)

                calls = ApiCalls(clock.now())
                assertTrue(calls.callBufferedApis(embrace))
                calls.callDroppedApis(embrace, noopSpans = false)

                embrace.start(context)
                assertEquals(SdkState.STARTED, sdkState)
                assertTrue(embrace.isStarted)
                assertNotNull(embrace.currentUserSessionId)
                recordSession {
                    embrace.startSpan(SPAN_1).stop()
                    embrace.logInfo(LOG_2)
                }
            },
            assertAction = {
                calls.assertDropped(logger, bufferedCallsDropped = true)

                // restarting does not re-enable data export, or replay calls made while disabled
                calls.assertOTelConfigNotApplied()
                assertNull(testRule.bootstrapper.essentialServiceModule.experimentTrackingService.getRecords())
                assertEquals(0, getLogEnvelopes(0).size)
                assertEquals(0, getSessionEnvelopes(0).size)
            },
        )
    }

    @Test
    fun `NOT_STARTED to STARTED - internal API switches from noop to the SDK`() {
        testRule.runTest(
            startSdk = false,
            testCaseAction = {
                assertInternalApiIsNoop()
                EmbraceInternalApi.internalInterface.addEnvelopeResource("before", "start")

                embrace.start(context)
                assertInternalApiIsLive()
                EmbraceInternalApi.internalInterface.addEnvelopeResource("after", "start")
                recordSession()
            },
            assertAction = {
                val extras = checkNotNull(getSingleSessionEnvelope().resource?.extras)
                assertEquals("start", extras["after"])
                assertFalse(extras.containsKey("before"))
            },
        )
    }

    @Test
    fun `NOT_STARTED to NOT_STARTED - internal API stays noop when start is rejected`() {
        testRule.runTest(
            persistedRemoteConfig = sdkDisabledConfig,
            expectSdkToStart = false,
            testCaseAction = {
                assertInternalApiIsNoop()
                embrace.start(context)
                assertEquals(SdkState.NOT_STARTED, sdkState)
                assertInternalApiIsNoop()
            },
        )
    }

    @Test
    fun `STARTED to DISABLED to STARTED - internal API follows SDK state`() {
        testRule.runTest(
            testCaseAction = {
                assertInternalApiIsLive()

                embrace.disable()
                assertEquals(SdkState.DISABLED, sdkState)
                assertInternalApiIsNoop()
                assertFalse(EmbraceInternalApi.internalInterface.isNetworkSpanForwardingEnabled())

                embrace.start(context)
                assertEquals(SdkState.STARTED, sdkState)
                assertInternalApiIsLive()
            },
            assertAction = {
                awaitStorageDeleted()
            },
        )
    }

    private fun assertInternalApiIsNoop() {
        assertSame(NoopInternalInterfaceApi, EmbraceInternalApi.internalInterfaceApi)
        assertSame(NoopInternalInterfaceApi.internalInterface, EmbraceInternalApi.internalInterface)
        assertSame(
            NoopInternalInterfaceApi.reactNativeInternalInterface,
            EmbraceInternalApi.reactNativeInternalInterface,
        )
        assertSame(NoopInternalInterfaceApi.unityInternalInterface, EmbraceInternalApi.unityInternalInterface)
        assertSame(NoopInternalInterfaceApi.flutterInternalInterface, EmbraceInternalApi.flutterInternalInterface)
    }

    private fun assertInternalApiIsLive() {
        assertTrue(EmbraceInternalApi.internalInterfaceApi is EmbraceImpl)
        assertNotSame(NoopInternalInterfaceApi.internalInterface, EmbraceInternalApi.internalInterface)
        assertNotSame(
            NoopInternalInterfaceApi.reactNativeInternalInterface,
            EmbraceInternalApi.reactNativeInternalInterface,
        )
        assertNotSame(NoopInternalInterfaceApi.unityInternalInterface, EmbraceInternalApi.unityInternalInterface)
        assertNotSame(NoopInternalInterfaceApi.flutterInternalInterface, EmbraceInternalApi.flutterInternalInterface)
    }

    private fun awaitStorageDeleted() {
        returnIfConditionMet(
            desiredValueSupplier = { true },
            dataProvider = {
                embraceDirs.values.all {
                    !it.sentinelFile().exists()
                }
            },
            condition = { it },
        )
    }

    /**
     * Calls every [SdkApi] function, split by those that are buffered and those that are dropped.
     */
    private class ApiCalls(val startMs: Long) {

        val endMs: Long = startMs + 50L
        val spanExporter = FakeSpanExporter()
        val spanProcessor = FakeSpanProcessor()
        val logRecordExporter = FakeLogRecordExporter()
        val logRecordProcessor = FakeLogRecordProcessor()

        val experimentRecords: String =
            "e:exp-a:a:$startMs:$endMs;" +
                "e:exp-b:b:$startMs:$endMs;" +
                "e:exp-c:c:$startMs;" +
                "f:flag-a:on:$startMs:$endMs;" +
                "f:flag-b::$startMs:$endMs;" +
                "f:flag-c:x:$startMs"

        /**
         * Calls every function that is buffered before start.
         */
        fun callBufferedApis(embrace: SdkApi): Boolean {
            embrace.addSpanExporter(spanExporter)
            embrace.addSpanProcessor(spanProcessor)
            embrace.addLogRecordExporter(logRecordExporter)
            embrace.addLogRecordProcessor(logRecordProcessor)
            embrace.setResourceAttribute(RESOURCE_ATTR_KEY, RESOURCE_ATTR_VALUE)
            embrace.trackExperiment("exp-a", "a", startMs)
            embrace.trackExperiments(
                listOf(
                    embrace.createExperiment("exp-b", "b", startMs),
                    embrace.createExperiment("exp-c", "c", startMs),
                ),
            )
            embrace.untrackExperiment("exp-a", endMs)
            embrace.untrackExperiments(listOf("exp-b"), endMs)
            embrace.trackFeatureFlag("flag-a", "on", startMs)
            embrace.trackFeatureFlags(
                listOf(
                    embrace.createFeatureFlag("flag-b", startedAt = startMs),
                    embrace.createFeatureFlag("flag-c", "x", startMs),
                ),
            )
            embrace.untrackFeatureFlag("flag-a", endMs)
            embrace.untrackFeatureFlags(listOf("flag-b"), endMs)
            return embrace.recordCompletedSpan(
                name = COMPLETED_SPAN,
                startTimeMs = startMs,
                endTimeMs = endMs,
                attributes = mapOf("key" to "value"),
            )
        }

        /**
         * Calls every function that is dropped when the SDK is not started.
         */
        fun callDroppedApis(embrace: SdkApi, noopSpans: Boolean = true) {
            val activity = Robolectric.buildActivity(Activity::class.java).get()
            embrace.applicationInitStart()
            embrace.applicationInitEnd()
            embrace.logMessage(LOG_1, Severity.INFO)
            embrace.logMessage(LOG_1, Severity.INFO, emptyMap(), ByteArray(1))
            embrace.logMessage(LOG_1, Severity.INFO, emptyMap(), "attachment-id", "https://example.com")
            embrace.logInfo(LOG_1)
            embrace.logWarning(LOG_1)
            embrace.logError(LOG_1)
            embrace.logException(IllegalStateException(LOG_1))
            embrace.logCustomStacktrace(Thread.currentThread().stackTrace)
            embrace.logPushNotification(
                title = "title",
                body = "body",
                topic = "topic",
                id = "id",
                notificationPriority = 1,
                messageDeliveredPriority = 1,
                isNotification = true,
                hasData = true,
            )
            embrace.recordNetworkRequest(
                EmbraceNetworkRequest.fromCompletedRequest(
                    "https://example.com",
                    HttpMethod.GET,
                    startMs,
                    endMs,
                    0,
                    0,
                    200,
                ),
            )
            val modifier = HttpRequestInfoModifier { }
            embrace.addHttpRequestInfoModifier(modifier)
            embrace.removeHttpRequestInfoModifier(modifier)
            assertFalse(embrace.addUserSessionProperty("key", "value", PropertyScope.USER_SESSION))
            assertFalse(embrace.removeUserSessionProperty("key"))
            embrace.endUserSession()
            val listener = UserSessionListener { }
            embrace.addUserSessionListener(listener)
            embrace.removeUserSessionListener(listener)
            embrace.setUserIdentifier("user-id")
            embrace.clearUserIdentifier()
            embrace.setUserEmail("user@example.com")
            embrace.clearUserEmail()
            embrace.addUserPersona("persona")
            embrace.clearUserPersona("persona")
            embrace.clearAllUserPersonas()
            embrace.setUsername("username")
            embrace.clearUsername()
            embrace.addBreadcrumb("breadcrumb")
            embrace.appReady()
            embrace.activityLoaded(activity)
            embrace.addLoadTraceAttribute(activity, "key", "value")
            embrace.addLoadTraceChildSpan(activity, "child", startMs, endMs)
            embrace.addLoadTraceChildSpan(activity, "child", startMs, endMs, emptyMap(), emptyList(), null)
            embrace.addStartupTraceAttribute("key", "value")
            embrace.addStartupTraceChildSpan("child", startMs, endMs)
            embrace.addStartupTraceChildSpan("child", startMs, endMs, emptyMap(), emptyList(), null)
            embrace.observeNavigation(activity, Any())
            assertFalse(embrace.startView("view"))
            assertFalse(embrace.endView("view"))
            assertEquals(noopSpans, embrace.createSpan("span") === NoopEmbraceSdkSpan)
            assertEquals(noopSpans, embrace.startSpan("span") === NoopEmbraceSdkSpan)
            assertEquals("result", embrace.recordSpan("span") { "result" })
            assertNull(embrace.getSpan("0000000000000000"))
            assertNotNull(embrace.generateW3cTraceparent())
            assertNotNull(embrace.getOpenTelemetryKotlin())
            assertEquals(0, embrace.getSdkCurrentTimeMs())
            assertFalse(embrace.isStarted)
            assertEquals("", embrace.deviceId)
            assertNull(embrace.currentUserSessionId)
            assertEquals(LastRunEndState.INVALID, embrace.lastRunEndState)
            assertFalse(EmbraceInternalApi.internalInterface.isNetworkSpanForwardingEnabled())
        }

        /**
         * Asserts that every dropped call was reported
         */
        fun assertDropped(logger: FakeInternalLogger, bufferedCallsDropped: Boolean = false) {
            val droppedExperimentCalls = listOf(
                "track_experiment",
                "track_experiment",
                "untrack_experiment",
                "untrack_experiment",
                "track_feature_flag",
                "track_feature_flag",
                "untrack_feature_flag",
                "untrack_feature_flag",
            ).takeIf { bufferedCallsDropped }.orEmpty()
            assertNoInternalErrors(logger)
            assertEquals(
                droppedExperimentCalls + List(6) { "log_message" } + listOf(
                    "log_push_notification",
                    "record_network_request",
                    "add_http_request_info_modifier",
                    "remove_http_request_info_modifier",
                    "add_session_property",
                    "remove_session_property",
                    "end_session",
                    "add_user_session_listener",
                    "remove_user_session_listener",
                    "set_user_identifier",
                    "clear_user_identifier",
                    "set_user_email",
                    "clear_user_email",
                    "add_user_persona",
                    "clear_user_persona",
                    "clear_user_personas",
                    "set_username",
                    "clear_username",
                    "add_breadcrumb",
                    "app_ready",
                    "activity_fully_loaded",
                    "add_load_trace_attribute",
                    "add_load_trace_child_span",
                    "add_load_trace_child_span",
                    "add_startup_trace_attribute",
                    "add_startup_trace_child_span",
                    "add_startup_trace_child_span",
                    "observe_navigation",
                    "start_view",
                    "end_view",
                    "get_device_id",
                ),
                logger.sdkNotInitializedMessages.map { it.msg },
            )
        }

        /**
         * Asserts that the exporters and processors added by [callBufferedApis] received the given telemetry.
         */
        fun assertOTelConfigApplied(spanName: String, logBody: String) {
            assertTrue(spanExporter.exportedSpans.any { it.name == spanName })
            assertTrue(spanProcessor.endedSpanNames.contains(spanName))
            assertTrue(logRecordExporter.exportedLogs.any { it.body == logBody })
            assertEquals(listOf(logBody), logRecordProcessor.processedLogBodies)
        }

        /**
         * Asserts that the exporters and processors added by [callBufferedApis] received no telemetry.
         */
        fun assertOTelConfigNotApplied() {
            assertEquals(emptyList<SpanData>(), spanExporter.exportedSpans)
            assertEquals(emptyList<String>(), spanProcessor.endedSpanNames)
            assertEquals(emptyList<LogRecordData>(), logRecordExporter.exportedLogs)
            assertEquals(emptyList<String>(), logRecordProcessor.processedLogBodies)
        }
    }
}
