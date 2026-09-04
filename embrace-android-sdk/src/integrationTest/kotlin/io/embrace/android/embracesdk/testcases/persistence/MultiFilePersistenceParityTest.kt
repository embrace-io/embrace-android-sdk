package io.embrace.android.embracesdk.testcases.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.assertions.findEventsOfType
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.findSpanByName
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.session.getSessionProperty
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.testcases.features.createNativeSymbolsForCurrentArch
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.Placeholder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.io.File

/**
 * Verifies that a session payload reaches the server, and looks the same, whether it was persisted
 * by the legacy single-file layer or by the multi-file layer.
 *
 * Every case runs under both layers. Only one of them may deliver a given session part - see
 * [deliveredParts].
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class MultiFilePersistenceParityTest(
    private val persistenceMode: PersistenceMode,
) {

    internal enum class PersistenceMode { LEGACY, MULTI_FILE }

    private val backgroundActivityEnabled = BackgroundActivityRemoteConfig(100f)

    /**
     * The remote config for the layer under test.
     */
    private fun remoteConfig(
        backgroundActivity: BackgroundActivityRemoteConfig = backgroundActivityEnabled,
    ) = RemoteConfig(
        pctMultiFilePersistenceEnabled = when (persistenceMode) {
            PersistenceMode.MULTI_FILE -> 100.0f
            PersistenceMode.LEGACY -> null
        },
        backgroundActivityConfig = backgroundActivity,
    )

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
        ).apply {
            getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.IoRegWorker).blockingMode = false
        }
    }

    @Test
    fun `one session payload is delivered`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertMinimalSessionPayload(deliveredParts().single())
            },
        )
    }

    @Test
    fun `the session part span matches the golden file`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertMatchesGoldenFile(deliveredParts().single())
            },
        )
    }

    @Test
    fun `spans completed during the session are persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession {
                    embrace.recordSpan("completed-span") {
                        clock.tick(100)
                    }
                    val span = checkNotNull(embrace.startSpan("stopped-span"))
                    clock.tick(50)
                    span.stop()
                }
            },
            assertAction = {
                val names = deliveredParts().single().allSpanNames()
                assertTrue("completed-span missing: $names", "completed-span" in names)
                assertTrue("stopped-span missing: $names", "stopped-span" in names)
            },
        )
    }

    @Test
    fun `a span still in flight at session end is persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession {
                    checkNotNull(embrace.startSpan("in-flight-span"))
                }
            },
            assertAction = {
                val names = deliveredParts().single().allSpanNames()
                assertTrue("in-flight-span missing: $names", "in-flight-span" in names)
            },
        )
    }

    @Test
    fun `breadcrumbs recorded during the session are persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession {
                    embrace.addBreadcrumb("Hello, world!")
                    clock.tick(1000)
                    embrace.addBreadcrumb("Bye, world!")
                }
            },
            assertAction = {
                val sessionSpan = deliveredParts().single().findSessionPartSpan()
                assertEquals(2, sessionSpan.findEventsOfType(EmbType.System.Breadcrumb).size)
            },
        )
    }

    @Test
    fun `session properties are persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            setupAction = {
                setupPermanentUserSessionProperties(mapOf("seeded" to "value"))
            },
            testCaseAction = {
                recordSession {
                    embrace.addUserSessionProperty("permanent", "value", PropertyScope.PERMANENT)
                    embrace.addUserSessionProperty("temporary", "value", PropertyScope.USER_SESSION)
                }
            },
            assertAction = {
                val sessionSpan = deliveredParts().single().findSessionPartSpan()
                listOf("seeded", "permanent", "temporary").forEach {
                    assertNotNull("missing session property '$it'", sessionSpan.getSessionProperty(it))
                }
            },
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun `user info changed mid-session is persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession {
                    embrace.setUserIdentifier("newId")
                    embrace.setUsername("newUserName")
                    embrace.setUserEmail("new@domain.com")
                }
            },
            assertAction = {
                val metadata = checkNotNull(deliveredParts().single().metadata)
                assertEquals("newId", metadata.userId)
                assertEquals("newUserName", metadata.username)
                assertEquals("new@domain.com", metadata.email)
            },
        )
    }

    @Test
    fun `a recorded network request is persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession {
                    embrace.recordNetworkRequest(
                        EmbraceNetworkRequest.fromCompletedRequest(
                            "https://embrace.io",
                            HttpMethod.GET,
                            clock.now(),
                            clock.now() + 100,
                            100,
                            1000,
                            200,
                        ),
                    )
                }
            },
            assertAction = {
                val envelope = deliveredParts().single()
                assertEquals(1, envelope.findSpansOfType(EmbType.Performance.Network).size)
            },
        )
    }

    @Test
    fun `the native symbol map is persisted identically`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                symbols = createNativeSymbolsForCurrentArch(mapOf("libfoo.so" to "symbol_content")),
            ),
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertEquals(
                    mapOf("libfoo.so" to "symbol_content"),
                    deliveredParts().single().data.sharedLibSymbolMapping,
                )
            },
        )
    }

    @Test
    fun `each session part in a process is persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession {
                    embrace.recordSpan("first-part-span") { clock.tick(100) }
                }
                clock.tick(20000)
                recordSession {
                    embrace.recordSpan("second-part-span") { clock.tick(100) }
                }
            },
            assertAction = {
                val parts = deliveredParts(expectedParts = 2)
                parts.forEach(::assertMinimalSessionPayload)
                assertEquals(
                    setOf("first-part-span", "second-part-span"),
                    parts.map { part -> part.allSpanNames().single { it.endsWith("-part-span") } }.toSet(),
                )
            },
        )
    }

    @Test
    fun `background activity parts are persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val envelope = deliveredParts(state = ProcessState.BACKGROUND).single()
                assertEquals(
                    "background",
                    envelope.findSessionPartSpan().attributes?.findAttributeValue(EmbSessionAttributes.EMB_STATE),
                )
            },
        )
    }

    @Test
    fun `a periodic write before the session ends does not change what is persisted`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession {
                    embrace.recordSpan("before-the-periodic-write") { clock.tick(100) }
                    clock.tick(2000)
                    testRule.setup.getFakedWorkerExecutor(Worker.Background.PeriodicCacheWorker)
                        .runCurrentlyBlocked()
                    embrace.recordSpan("after-the-periodic-write") { clock.tick(100) }
                }
            },
            assertAction = {
                val names = deliveredParts().single().allSpanNames()
                assertTrue("before-the-periodic-write missing: $names", "before-the-periodic-write" in names)
                assertTrue("after-the-periodic-write missing: $names", "after-the-periodic-write" in names)
            },
        )
    }

    @Test
    fun `spans completed before the first session part directory exists are persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(backgroundActivity = BackgroundActivityRemoteConfig(0f)),
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val names = deliveredParts().single().allSpanNames()
                assertTrue("emb-sdk-init missing from: $names", "emb-sdk-init" in names)
            },
        )
    }

    @Test
    fun `a span that stops after its session part ended is persisted identically`() {
        lateinit var span: EmbraceSpan
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(backgroundActivity = BackgroundActivityRemoteConfig(0f)),
            testCaseAction = {
                recordSession {
                    span = checkNotNull(embrace.startSpan("late-span"))
                }
                clock.tick(100)
                span.stop()
                clock.tick(20000)
                recordSession()
            },
            assertAction = {
                val parts = deliveredParts(expectedParts = 2)
                assertTrue(
                    "late-span missing from every payload",
                    parts.any { "late-span" in it.allSpanNames() },
                )
            },
        )
    }

    @Test
    fun `no span is created while no session part is active`() {
        var blockRan = false
        lateinit var orphaned: EmbraceSpan
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(backgroundActivity = BackgroundActivityRemoteConfig(0f)),
            testCaseAction = {
                recordSession()
                orphaned = embrace.startSpan("started-span")
                embrace.recordSpan("recorded-span") {
                    blockRan = true
                    clock.tick(100)
                }
                clock.tick(20000)
                recordSession()
            },
            assertAction = {
                assertFalse("a span was started with no active session part", orphaned.isRecording)
                assertNull("a span was started with no active session part", orphaned.spanId)
                assertTrue("recordSpan did not run the block it was given", blockRan)

                deliveredParts(expectedParts = 2).forEach { part ->
                    val names = part.allSpanNames()
                    assertFalse("started-span was persisted: $names", "started-span" in names)
                    assertFalse("recorded-span was persisted: $names", "recorded-span" in names)
                }
            },
        )
    }

    @Test
    fun `an empty span event collection survives being persisted`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession {
                    embrace.recordSpan("plain-span") {
                        clock.tick(100)
                    }
                }
            },
            assertAction = {
                val span = checkNotNull(deliveredParts().single().findSpanByName("plain-span"))
                assertEquals(emptyList<SpanEvent>(), span.events)
                assertEquals(
                    listOf("END_SESSION_PART"),
                    span.links.orEmpty().map { it.attributes?.findAttributeValue("emb.link_type") },
                )
            },
        )
    }

    @Test
    fun `the session span records the process that created it`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val processId = deliveredParts().single()
                    .findSessionPartSpan().attributes?.findAttributeValue(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER)
                assertNotNull("the session span has no process identifier", processId)
            },
        )
    }

    @Test
    fun `a crashed session part is handed to the intake service rather than delivered`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession {
                    simulateJvmUncaughtException(RuntimeException("Boom!"))
                }
            },
            assertAction = {
                assertEquals(0, getSessionEnvelopes(0).size)
                assertTrue(
                    "the crashed session part was left behind on disk",
                    storedSessionPartDirectories().isEmpty(),
                )
            },
        )
    }

    /**
     * Returns the envelope delivered for each session part.
     *
     * Exactly one envelope is expected per part: only one persistence layer may own a part, so a
     * second envelope for the same part means both layers delivered it. `getSessionEnvelopes`
     * waits for an exact count, so a duplicate delivery fails here rather than passing silently.
     */
    private fun EmbracePayloadAssertionInterface.deliveredParts(
        expectedParts: Int = 1,
        state: ProcessState = ProcessState.FOREGROUND,
    ): List<Envelope<SessionPartPayload>> {
        val envelopes = getSessionEnvelopes(expectedParts, state, assertOrdering = false)
        assertEquals(
            "wrong number of distinct session parts delivered",
            expectedParts,
            envelopes.map(Envelope<SessionPartPayload>::getSessionPartId).distinct().size,
        )
        return envelopes
    }

    private fun EmbracePayloadAssertionInterface.assertMatchesGoldenFile(
        envelope: Envelope<SessionPartPayload>,
    ) {
        val sessionSpan = envelope.findSessionPartSpan()
        validatePayloadAgainstGoldenFile(
            payload = sessionSpan.copy(attributes = sessionSpan.attributes?.sorted()),
            goldenFileName = GOLDEN_FILE,
            placeholders = mapOf(
                Placeholder.USER_SESSION_ID to envelope.getUserSessionId(),
                Placeholder.SESSION_PART_ID to envelope.getSessionPartId(),
            ),
        )
    }

    /**
     * Asserts the basic shape of a delivered session payload. Both persistence paths must satisfy
     * this identically.
     */
    private fun assertMinimalSessionPayload(envelope: Envelope<SessionPartPayload>) {
        assertEquals("spans", envelope.type)
        assertEquals("2.5.1", envelope.resource?.appVersion)

        val sessionSpan = envelope.findSessionPartSpan()
        assertEquals(SESSION_SPAN_NAME, sessionSpan.name)
        assertNotNull(sessionSpan.endTimeNanos)
        assertEquals("foreground", sessionSpan.attributes?.findAttributeValue(EmbSessionAttributes.EMB_STATE))
    }

    private fun Envelope<SessionPartPayload>.allSpanNames(): List<String> =
        (data.spans.orEmpty() + data.spanSnapshots.orEmpty()).mapNotNull(Span::name)

    private fun storedSessionPartDirectories(): List<SessionPartDirectory> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val sessionsDir: File = StorageLocation.SESSION_SPLIT.asFile(
            logger = FakeInternalLogger(),
            rootDirSupplier = { ctx.filesDir },
            fallbackDirSupplier = { ctx.cacheDir },
        ).value
        return (sessionsDir.list() ?: emptyArray()).mapNotNull(SessionPartDirectory::fromDirName)
    }

    /**
     * Sorts a span's attributes: an attribute map is unordered by nature, so the golden file can
     * only be compared against a stable ordering.
     */
    private fun List<Attribute>.sorted(): List<Attribute> = sortedWith(compareBy({ it.key }, { it.data }))

    internal companion object {
        private const val SESSION_SPAN_NAME = "emb-session"
        private const val GOLDEN_FILE = "multi_file_parity_session_part_span.json"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = PersistenceMode.entries.map { arrayOf<Any>(it) }
    }
}
