package io.embrace.android.embracesdk.testcases.persistence

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fixtures.fakeNativeCrashStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SpanCollection
import io.embrace.android.embracesdk.internal.session.persistence.SpanProto
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.testcases.features.createNativeSymbolsForCurrentArch
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.actions.createStoredNativeCrashData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.File

/**
 * Covers the session part left behind by a *dirty death*: a process that died without a JVM crash,
 * such as an OOM kill or a native crash. The session part span never
 * ended so must be resurrected from its span snapshot before delivery can happen.
 *
 * Tests are currently ignored because the multi-file persistence layer does not handle resurrection correctly.
 */
@RunWith(AndroidJUnit4::class)
internal class MultiFilePersistenceDirtyDeathTest {

    private val serializer = TestPlatformSerializer()
    private val fakeSymbols = mapOf("libfoo.so" to "symbol_content")
    private val liveConfig = RemoteConfig(
        pctMultiFilePersistenceEnabled = 100.0f,
        backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
    )
    private val seededConfig = RemoteConfig(pctMultiFilePersistenceEnabled = 100.0f)

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(
            workersToFake = listOf(
                Worker.Background.SessionPersistenceWorker,
                Worker.Background.PeriodicCacheWorker,
                Worker.Background.NonIoRegWorker,
                Worker.Background.IoRegWorker,
            ),
            ignoredInternalErrors = listOf(InternalErrorType.NativeCrashResurrectionError),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.IoRegWorker).blockingMode = false
        }
    }

    @Ignore
    @Test
    fun `a session part left behind by a dirty death is delivered with its session span`() {
        lateinit var deadProcessId: String
        dirtyDeathThenRelaunch(
            onDeath = { deadProcessId = testRule.bootstrapper.openTelemetryModule.otelSdkConfig.processIdentifier },
            assertDelivered = { envelope ->
                assertEquals(
                    "the session span was not resurrected into a completed span",
                    1,
                    envelope.data.spans.orEmpty().count { it.hasEmbraceAttribute(EmbType.Ux.Session) },
                )
                assertEquals(
                    "the delivered part still carries snapshots",
                    emptyList<Span>(),
                    envelope.data.spanSnapshots.orEmpty(),
                )
                val sessionSpan = envelope.findSessionPartSpan()
                assertNotNull("the resurrected session span has no end time", sessionSpan.endTimeNanos)
                assertEquals(
                    deadProcessId,
                    sessionSpan.attributes?.findAttributeValue(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER),
                )
            },
        )
    }

    @Ignore
    @Test
    fun `a span in flight at a dirty death is delivered as a failed span`() {
        dirtyDeathThenRelaunch(
            assertDelivered = { envelope ->
                val span = envelope.data.spans.orEmpty().singleOrNull { it.name == IN_FLIGHT_SPAN_NAME }
                assertNotNull(
                    "the in-flight span was not resurrected: ${envelope.allSpanNames()}",
                    span,
                )
                assertEquals(Span.Status.ERROR, checkNotNull(span).status)
                // TODO: future: endTimeNanos should be derived from the latest timestamp in all the spans and span snapshots
                assertNotNull("the failed span has no end time", span.endTimeNanos)
            },
        )
    }

    @Ignore
    @Test
    fun `the final session part of a terminated user session left by a dirty death is stamped`() {
        deliverSeededDirtyPart(
            seed = {
                persistExpiredUserSession(
                    sdkStartTimeMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS,
                    userSessionId = SdkIntegrationTestRule.DEFAULT_EXPIRED_USER_SESSION_ID,
                )
            },
            assertDelivered = { envelope ->
                val attributes = envelope.findSessionPartSpan().attributes
                assertEquals("1", attributes?.findAttributeValue(EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART))
                assertNotNull(
                    "the terminated user session's reason was not stamped",
                    attributes?.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON),
                )
            },
        )
    }

    @Ignore
    @Test
    fun `a background only session part left behind by a dirty death is stamped`() {
        deliverSeededDirtyPart(
            seed = {
                persistExpiredUserSession(
                    sdkStartTimeMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS,
                    userSessionId = SdkIntegrationTestRule.DEFAULT_EXPIRED_USER_SESSION_ID,
                    isBackgroundOnly = true,
                )
            },
            assertDelivered = { envelope ->
                assertEquals(
                    "1",
                    envelope.findSessionPartSpan().attributes
                        ?.findAttributeValue(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART),
                )
            },
        )
    }

    @Ignore
    @Test
    fun `a native crash is attached to a session part left behind by a dirty death`() {
        val crashData = createStoredNativeCrashData(
            serializer = serializer,
            resourceFixtureName = "native_crash_1.txt",
            crashMetadata = fakeNativeCrashStoredTelemetryMetadata,
        )
        val nativeCrash = crashData.nativeCrash
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
                symbols = createNativeSymbolsForCurrentArch(fakeSymbols),
            ),
            persistedRemoteConfig = seededConfig,
            setupAction = {
                persistSessionPart(
                    userSessionId = nativeCrash.userSessionId,
                    sessionPartId = nativeCrash.sessionPartId,
                    sealed = false,
                )
                setupFakeNativeCrash(serializer, crashData)
            },
            testCaseAction = {},
            assertAction = {
                val envelope = getSessionEnvelopes(1, assertOrdering = false).single()
                assertEquals(
                    nativeCrash.nativeCrashId,
                    envelope.findSessionPartSpan().attributes?.findAttributeValue(EmbSessionAttributes.EMB_CRASH_ID),
                )
                assertNativeCrashSent(getSingleLogEnvelope().getLastLog(), crashData, fakeSymbols)
            },
        )
    }

    /**
     * Runs a session that is still open when the process dies, tears the process down without a
     * clean shutdown, then relaunches and hands the delivered part to [assertDelivered].
     */
    private fun dirtyDeathThenRelaunch(
        onDeath: () -> Unit = {},
        assertDelivered: EmbracePayloadAssertionInterface.(Envelope<SessionPartPayload>) -> Unit,
    ) {
        testRule.runTest(
            persistedRemoteConfig = liveConfig,
            testCaseAction = {
                simulateOpeningActivities(
                    addStartupActivity = false,
                    startInBackground = true,
                    endInBackground = false, // don't end
                    activitiesAndActions = listOf(
                        Robolectric.buildActivity(Activity::class.java) to {
                            embrace.startSpan(IN_FLIGHT_SPAN_NAME)
                            clock.tick(SESSION_DURATION_MS)
                        },
                    ),
                )
                testRule.setup.getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker)
                    .moveForwardAndRunBlocked(WRITE_DEBOUNCE_WAIT_MS * 2)
            },
            assertAction = {
                assertEquals("a session part was delivered by the dying process", 0, getSessionEnvelopes(0).size)
                assertUnsealedPartOnDisk()
                onDeath()
            },
        )

        // tear the dead process down so storage is read from scratch on next launch
        testRule.bootstrapper.stop()

        testRule.runTest(
            persistedRemoteConfig = liveConfig,
            testCaseAction = {},
            assertAction = {
                assertDelivered(getSessionEnvelopes(1, assertOrdering = false).single())
            },
        )
    }

    /**
     * Writes an unsealed session part to disk as a dead process would have left it, then starts the
     * SDK
     */
    private fun deliverSeededDirtyPart(
        seed: EmbraceSetupInterface.() -> Unit,
        assertDelivered: EmbracePayloadAssertionInterface.(Envelope<SessionPartPayload>) -> Unit,
    ) {
        testRule.runTest(
            persistedRemoteConfig = seededConfig,
            setupAction = {
                persistSessionPart(
                    userSessionId = SdkIntegrationTestRule.DEFAULT_EXPIRED_USER_SESSION_ID,
                    sessionPartId = SdkIntegrationTestRule.DEFAULT_DEAD_SESSION_PART_ID,
                    sealed = false,
                )
                seed()
            },
            testCaseAction = {},
            assertAction = {
                assertDelivered(getSessionEnvelopes(1, assertOrdering = false).single())
            },
        )
    }

    /**
     * Asserts the dying process left the part on disk as expected.
     */
    private fun assertUnsealedPartOnDisk() {
        val directory = checkNotNull(storedSessionPartDirectories().singleOrNull()) {
            "expected exactly one session part on disk, found ${storedSessionPartDirectories()}"
        }
        assertTrue(
            "the session span was sealed, so the process did not die dirty",
            sessionSpansIn(directory, COMPLETED_SPANS_FILE_NAME).isEmpty(),
        )
        val snapshot = sessionSpansIn(directory, SPAN_SNAPSHOTS_FILE_NAME).singleOrNull()
        assertNotNull("the session span was not persisted as a snapshot", snapshot)
        assertNull("the session span snapshot has an end time", checkNotNull(snapshot).end_time_unix_nano)
    }

    private fun sessionSpansIn(directory: SessionPartDirectory, fileName: String): List<SpanProto> {
        val bytes = File(File(sessionsDir(), directory.dirName), fileName)
            .takeIf(File::isFile)
            ?.readBytes()
            ?: return emptyList()
        return SpanCollection.ADAPTER.decode(bytes).spans.filter { span ->
            span.attributes.any { it.key == "emb.type" && it.value_ == "ux.session" }
        }
    }

    private fun storedSessionPartDirectories(): List<SessionPartDirectory> =
        (sessionsDir().list() ?: emptyArray()).mapNotNull(SessionPartDirectory::fromDirName)

    private fun sessionsDir(): File {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return StorageLocation.SESSION_SPLIT.asFile(
            logger = FakeInternalLogger(),
            rootDirSupplier = { ctx.filesDir },
            fallbackDirSupplier = { ctx.cacheDir },
        ).value
    }

    private fun Envelope<SessionPartPayload>.allSpanNames(): List<String> =
        (data.spans.orEmpty() + data.spanSnapshots.orEmpty()).mapNotNull(Span::name)

    private companion object {
        private const val IN_FLIGHT_SPAN_NAME = "in-flight-span"
        private const val SESSION_DURATION_MS = 30000L
        private const val WRITE_DEBOUNCE_WAIT_MS = 1000L
        private const val COMPLETED_SPANS_FILE_NAME = "completed_spans.pb"
        private const val SPAN_SNAPSHOTS_FILE_NAME = "span_snapshots.pb"
    }
}
