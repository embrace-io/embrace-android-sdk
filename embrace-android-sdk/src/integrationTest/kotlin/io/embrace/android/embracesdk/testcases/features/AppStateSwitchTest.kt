package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.findSpansByName
import io.embrace.android.embracesdk.assertions.getLogs
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.fakes.config.FakeAppStateLocalConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeKey
import io.embrace.android.embracesdk.internal.session.getStateSpan
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * When the app state data model is on, and what the SDK does when it is not.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class AppStateSwitchTest(otelSdkMode: OtelSdkMode) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode) {
        EmbraceSetupInterface(
            workersToFake = listOf(
                Worker.Background.SessionPersistenceWorker,
                Worker.Background.PeriodicCacheWorker,
                Worker.Background.NonIoRegWorker,
                Worker.Background.StateExportWorker,
                Worker.Background.IoRegWorker,
            ),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.StateExportWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.IoRegWorker).blockingMode = false
        }
    }

    private val noAppId = FakeInstrumentedConfig(project = FakeProjectConfig(appId = null))

    private val legacyTestStateKey = "${AppStateKeys.LEGACY_STEM}test"

    @Test
    fun `the model is off by default and state capture is the earlier model`() {
        runWithEarlierModel(RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)))
    }

    @Test
    fun `the model switch alone is not enough without multi-file persistence`() {
        runWithEarlierModel(
            RemoteConfig(
                pctStateCaptureEnabledV2 = 100f,
                pctSharedStateSpanEnabled = 100f,
                pctStateAssociationEnabled = 100f,
                pctMultiFilePersistenceEnabled = 0f,
                backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
            )
        )
    }

    @Test
    fun `with state capture off neither model captures anything`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                pctStateCaptureEnabledV2 = 0f,
                pctSharedStateSpanEnabled = 100f,
                pctStateAssociationEnabled = 100f,
                pctMultiFilePersistenceEnabled = 100f,
                backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
            ),
            testCaseAction = {
                recordSession {
                    embrace.logInfo("ordinary")
                    embrace.startSpan("ordinary")?.stop()
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                assertTrue(envelope.findSpansByName(AppStateKeys.CHANGE_LOG_SPAN_NAME).isEmpty())
                assertTrue(envelope.findSpansByName("emb-state-test").isEmpty())
                assertTrue(checkNotNull(envelope.data.spans).none { span -> span.attributes.orEmpty().any { it.isStateKey() } })

                val log = getSingleLogEnvelope().getLogs { it.body == "ordinary" }.single()
                assertTrue(log.attributes.orEmpty().none { it.isStateKey() })
            },
            otelExportAssertion = {
                val span = awaitSpans(1) { it.name == "ordinary" }.single()
                assertTrue(span.attributes.toStringMap().keys.none { it.startsWith(AppStateKeys.STEM) })
                val log = awaitLogs(1) { it.bodyValue?.asString() == "ordinary" }.single()
                assertTrue(log.attributes.toStringMap().keys.none { it.startsWith(AppStateKeys.STEM) || it.startsWith(LEGACY_STEM) })
                assertFalse(log.resource.attributes.toStringMap().containsKey(AppStateKeys.PROCESS_IDENTIFIER))
                assertTrue(awaitLogs(0) { it.eventName?.startsWith(AppStateKeys.STEM) == true }.isEmpty())
            },
        )
    }

    @Test
    fun `an app with no app id switched on locally gives its exporters the model without sending anything to Embrace`() {
        testRule.runTest(
            instrumentedConfig = noAppId,
            setupAction = {
                getFakeErrorHandler().throwOnSdkError = false
                appStateLocalConfig = FakeAppStateLocalConfig(enabled = true)
            },
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    embrace.logInfo("ordinary")
                    state.onStateChange(newState = "foo", transitionTimeMs = clock.tick(100L))
                    val span = checkNotNull(embrace.startSpan("ordinary"))
                    state.onStateChange(newState = "bar", transitionTimeMs = clock.tick(100L))
                    span.stop()
                }
            },
            otelExportAssertion = {
                val log = awaitLogs(1) { it.bodyValue?.asString() == "ordinary" }.single()
                assertNotNull(log.attributes.get(AttributeKey.longKey(AppStateKeys.SEQ)))
                assertNull(log.attributes.toStringMap()[AppStateKeys.PACKED])
                // the log was written before the first change in the session
                assertEquals("UNKNOWN", log.attributes.toStringMap()[AppStateKeys.VALUE_PREFIX + "test"])

                val span = awaitSpans(1) { it.name == "ordinary" }.single().attributes
                val seq = checkNotNull(span.get(AttributeKey.longKey(AppStateKeys.SEQ)))
                assertEquals(seq + 1, span.get(AttributeKey.longKey(AppStateKeys.END_SEQ)))
                assertTrue(span.toStringMap().keys.none(AppStateKeys::isCustomerValueKey))

                val changes = awaitLogs(2) { it.isChangeOf("test") }
                assertEquals(listOf("foo", "bar"), changes.map { it.attributes.toStringMap()[AppStateKeys.NEW_VALUE] })
                assertEquals(AppStateKeys.CHANGE_EVENT_NAME, changes.first().eventName)
                assertNotNull(changes.first().resource.attributes.toStringMap()[AppStateKeys.PROCESS_IDENTIFIER])
            },
        )
    }

    @Test
    fun `an app with no app id switched on locally in verbose mode gives its exported spans the values`() {
        testRule.runTest(
            instrumentedConfig = noAppId,
            setupAction = {
                getFakeErrorHandler().throwOnSdkError = false
                appStateLocalConfig = FakeAppStateLocalConfig(enabled = true, exportMode = "verbose")
            },
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    state.onStateChange(newState = "before", transitionTimeMs = clock.tick(100L))
                    val span = checkNotNull(embrace.startSpan("ordinary"))
                    state.onStateChange(newState = "after", transitionTimeMs = clock.tick(100L))
                    span.stop()
                }
            },
            otelExportAssertion = {
                val span = awaitSpans(1) { it.name == "ordinary" }.single().attributes.toStringMap()
                assertEquals("before", span[AppStateKeys.VALUE_PREFIX + "test"])
                assertEquals("after", span[AppStateKeys.END_VALUE_PREFIX + "test"])
            },
        )
    }

    /**
     * Runs a session on [remoteConfig], which leaves the model off, and asserts that every piece of the earlier model is there and
     * none of the new one.
     */
    private fun runWithEarlierModel(remoteConfig: RemoteConfig) {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            testCaseAction = {
                recordSession {
                    findDataSource<TestStateDataSource>().onStateChange(newState = "foo", transitionTimeMs = clock.tick(100L))
                    embrace.logInfo("ordinary")
                    embrace.startSpan("ordinary")?.stop()
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                assertNotNull(envelope.getStateSpan("emb-state-test"))
                assertTrue(envelope.findSpansByName(AppStateKeys.CHANGE_LOG_SPAN_NAME).isEmpty())
                assertTrue(
                    checkNotNull(envelope.data.spans).none { span ->
                        span.attributes.orEmpty().any { it.key == AppStateKeys.SEQ || it.key == AppStateKeys.END_SEQ }
                    }
                )

                val attributes = getSingleLogEnvelope().getLogs { it.body == "ordinary" }.single().attributes.orEmpty()
                assertEquals("foo", attributes.findAttributeValue(legacyTestStateKey))
                assertNull(attributes.findAttributeValue(AppStateKeys.PACKED))
                assertFalse(attributes.hasEmbraceAttributeKey(AppStateKeys.SEQ))
            },
            otelExportAssertion = {
                val log = awaitLogs(1) { it.bodyValue?.asString() == "ordinary" }.single()
                val attributes = log.attributes.toStringMap()
                assertFalse(attributes.containsKey(AppStateKeys.VALUE_PREFIX + "test"))
                assertFalse(attributes.containsKey(AppStateKeys.SEQ))
                assertFalse(log.resource.attributes.toStringMap().containsKey(AppStateKeys.PROCESS_IDENTIFIER))

                val span = awaitSpans(1) { it.name == "ordinary" }.single()
                assertFalse(span.attributes.toStringMap().containsKey(AppStateKeys.SEQ))
                assertFalse(span.resource.attributes.toStringMap().containsKey(AppStateKeys.PROCESS_IDENTIFIER))
                assertTrue(awaitLogs(0) { it.eventName?.startsWith(AppStateKeys.STEM) == true }.isEmpty())
            },
        )
    }

    private fun io.embrace.android.embracesdk.internal.payload.Attribute.isStateKey(): Boolean =
        checkNotNull(key).let { it.startsWith(AppStateKeys.STEM) || it.startsWith(LEGACY_STEM) }

    private fun OtelJavaLogRecordData.isChangeOf(state: String): Boolean =
        eventName == AppStateKeys.CHANGE_EVENT_NAME && attributes.toStringMap()[AppStateKeys.NAME] == state

    internal companion object {
        private const val LEGACY_STEM = AppStateKeys.LEGACY_STEM

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
