package io.embrace.android.embracesdk.testcases.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.session.getSessionProperty
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.testcases.features.createNativeSymbolsForCurrentArch
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.Placeholder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies that a minimal session payload reaches the server whether it was persisted by the legacy
 * single-file layer or by the multi-file layer.
 */
@RunWith(AndroidJUnit4::class)
internal class MultiFilePersistenceParityTest {

    // TODO: future address test case failures

    private val backgroundActivityEnabled = BackgroundActivityRemoteConfig(100f)

    private val multiFileEnabled = RemoteConfig(
        pctMultiFilePersistenceEnabled = 100.0f,
        backgroundActivityConfig = backgroundActivityEnabled,
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
    fun `legacy single-file persistence delivers one session payload`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(backgroundActivityConfig = backgroundActivityEnabled),
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertMinimalSessionPayload(getSingleSessionEnvelope())
            },
        )
    }

    @Test
    fun `legacy session part span matches the golden file`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(backgroundActivityConfig = backgroundActivityEnabled),
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertMatchesGoldenFile(getSingleSessionEnvelope())
            },
        )
    }

    @Test
    fun `multi-file session part span matches the golden file`() {
        testRule.runTest(
            persistedRemoteConfig = multiFileEnabled,
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val (first, second) = deliveredPair()
                assertMatchesGoldenFile(first)
                assertMatchesGoldenFile(second)
            },
        )
    }

    @Test
    fun `multi-file persistence delivers the session payload once its writes complete`() {
        testRule.runTest(
            persistedRemoteConfig = multiFileEnabled,
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertMinimalSessionPayload(assertParity().single())
            },
        )
    }

    @Test
    fun `spans completed during the session are persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = multiFileEnabled,
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
                val names = assertParity().single().allSpanNames()
                assertTrue("completed-span missing: $names", "completed-span" in names)
                assertTrue("stopped-span missing: $names", "stopped-span" in names)
            },
        )
    }

    @Test
    fun `a span still in flight at session end is persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = multiFileEnabled,
            testCaseAction = {
                recordSession {
                    checkNotNull(embrace.startSpan("in-flight-span"))
                }
            },
            assertAction = {
                val names = assertParity().single().allSpanNames()
                assertTrue("in-flight-span missing: $names", "in-flight-span" in names)
            },
        )
    }

    @Test
    fun `breadcrumbs recorded during the session are persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = multiFileEnabled,
            testCaseAction = {
                recordSession {
                    embrace.addBreadcrumb("Hello, world!")
                    clock.tick(1000)
                    embrace.addBreadcrumb("Bye, world!")
                }
            },
            assertAction = {
                val sessionSpan = assertParity().single().findSessionPartSpan()
                assertEquals(2, sessionSpan.findEventsOfType(EmbType.System.Breadcrumb).size)
            },
        )
    }

    @Test
    fun `session properties are persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = multiFileEnabled,
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
                val sessionSpan = assertParity().single().findSessionPartSpan()
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
            persistedRemoteConfig = multiFileEnabled,
            testCaseAction = {
                recordSession {
                    embrace.setUserIdentifier("newId")
                    embrace.setUsername("newUserName")
                    embrace.setUserEmail("new@domain.com")
                }
            },
            assertAction = {
                val metadata = checkNotNull(assertParity().single().metadata)
                assertEquals("newId", metadata.userId)
                assertEquals("newUserName", metadata.username)
                assertEquals("new@domain.com", metadata.email)
            },
        )
    }

    @Test
    fun `a recorded network request is persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = multiFileEnabled,
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
                val envelope = assertParity().single()
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
            persistedRemoteConfig = multiFileEnabled,
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertEquals(
                    mapOf("libfoo.so" to "symbol_content"),
                    assertParity().single().data.sharedLibSymbolMapping,
                )
            },
        )
    }

    @Test
    fun `each session part in a process is persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = multiFileEnabled,
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
                val parts = assertParity(expectedParts = 2)
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
            persistedRemoteConfig = multiFileEnabled,
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val envelope = assertParity(state = ProcessState.BACKGROUND).single()
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
            persistedRemoteConfig = multiFileEnabled,
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
                val names = assertParity().single().allSpanNames()
                assertTrue("before-the-periodic-write missing: $names", "before-the-periodic-write" in names)
                assertTrue("after-the-periodic-write missing: $names", "after-the-periodic-write" in names)
            },
        )
    }

    @Ignore("Spans completed with no active session part are not persisted")
    @Test
    fun `spans completed while no session part is active are persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                pctMultiFilePersistenceEnabled = 100.0f,
                backgroundActivityConfig = BackgroundActivityRemoteConfig(0f),
            ),
            testCaseAction = {
                recordSession()
                embrace.recordSpan("orphaned-span") {
                    clock.tick(100)
                }
                clock.tick(20000)
                recordSession()
            },
            assertAction = {
                val parts = assertParity(expectedParts = 2)
                assertTrue(
                    "orphaned-span missing from every payload",
                    parts.any { "orphaned-span" in it.allSpanNames() },
                )
            },
        )
    }

    @Test
    fun `empty span collections are persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = multiFileEnabled,
            testCaseAction = {
                recordSession {
                    embrace.recordSpan("plain-span") {
                        clock.tick(100)
                    }
                }
            },
            assertAction = {
                val (first, second) = deliveredPair()
                assertEquals(first.findSpanByName("plain-span"), second.findSpanByName("plain-span"))
            },
        )
    }

    @Test
    fun `the session span links are persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = multiFileEnabled,
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val (first, second) = deliveredPair()
                assertEquals(first.findSessionPartSpan().links, second.findSessionPartSpan().links)
            },
        )
    }

    @Test
    fun `the session span process identifier is persisted identically`() {
        testRule.runTest(
            persistedRemoteConfig = multiFileEnabled,
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val (first, second) = deliveredPair()
                assertEquals(
                    first.findSessionPartSpan().attributes?.findAttributeValue(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER),
                    second.findSessionPartSpan().attributes?.findAttributeValue(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER),
                )
            },
        )
    }

    @Test
    fun `a crashed session part is handed to the intake service rather than delivered`() {
        testRule.runTest(
            persistedRemoteConfig = multiFileEnabled,
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
     * Returns one envelope per session part, having first asserted that the two envelopes delivered
     * for each part are identical.
     */
    private fun EmbracePayloadAssertionInterface.assertParity(
        expectedParts: Int = 1,
        state: ProcessState = ProcessState.FOREGROUND,
    ): List<Envelope<SessionPartPayload>> {
        val envelopes = getSessionEnvelopes(expectedParts * 2, state, assertOrdering = false)
        val byPart = envelopes.groupBy(Envelope<SessionPartPayload>::getSessionPartId)
        assertEquals("wrong number of distinct session parts delivered", expectedParts, byPart.size)

        return byPart.map { (sessionPartId, pair) ->
            assertEquals("session part $sessionPartId was not delivered twice", 2, pair.size)
            assertEnvelopesMatch(sessionPartId, pair.first(), pair.last())
            pair.first()
        }
    }

    /**
     * Returns the two envelopes delivered for a single session part without asserting anything
     * about them. Used by the tests that pin down one known divergence.
     */
    private fun EmbracePayloadAssertionInterface.deliveredPair(
        state: ProcessState = ProcessState.FOREGROUND,
    ): Pair<Envelope<SessionPartPayload>, Envelope<SessionPartPayload>> {
        val envelopes = getSessionEnvelopes(2, state, assertOrdering = false)
        assertEquals(
            "more than one session part was delivered",
            1,
            envelopes.map(Envelope<SessionPartPayload>::getSessionPartId).distinct().size,
        )
        return envelopes.first() to envelopes.last()
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

    private fun assertEnvelopesMatch(
        sessionPartId: String,
        first: Envelope<SessionPartPayload>,
        second: Envelope<SessionPartPayload>,
    ) {
        assertEquals("envelope version differs for part $sessionPartId", first.version, second.version)
        assertEquals("envelope type differs for part $sessionPartId", first.type, second.type)
        assertEquals("resource differs for part $sessionPartId", first.resource, second.resource)
        assertEquals("metadata differs for part $sessionPartId", first.metadata, second.metadata)
        assertEquals(
            "shared lib symbol mapping differs for part $sessionPartId",
            first.data.sharedLibSymbolMapping,
            second.data.sharedLibSymbolMapping,
        )
        assertSpansMatch("spans", sessionPartId, first.data.spans, second.data.spans)
        assertSpansMatch("span snapshots", sessionPartId, first.data.spanSnapshots, second.data.spanSnapshots)
    }

    /**
     * Compares two span lists ignoring their order: the legacy path emits spans in `SpanRepository`
     * flush order whereas the multi-file path replays the append order of `completed_spans.pb` with
     * the session span last. Each span is then compared by [normalised].
     */
    private fun assertSpansMatch(
        label: String,
        sessionPartId: String,
        first: List<Span>?,
        second: List<Span>?,
    ) {
        assertEquals(
            "one payload for part $sessionPartId has no $label and the other does",
            first == null,
            second == null,
        )
        val firstByKey = first.orEmpty().map { it.normalised() }.groupBy(::spanKey)
        val secondByKey = second.orEmpty().map { it.normalised() }.groupBy(::spanKey)
        assertEquals(
            "different $label in each payload for part $sessionPartId",
            firstByKey.keys.sorted(),
            secondByKey.keys.sorted(),
        )
        firstByKey.forEach { (key, spans) ->
            assertEquals("$label differ for '$key' in part $sessionPartId", spans, secondByKey.getValue(key))
        }
    }

    private fun spanKey(span: Span): String = "${span.name}[${span.spanId}]"

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
     * Sorts the attributes of a span, its events and its links: the two payloads are built from
     * separate reads of the same telemetry, so an attribute map that is unordered by nature can be
     * iterated in a different order for each of them.
     */
    private fun Span.normalised(): Span = copy(
        events = events?.map { it.normalised() },
        attributes = attributes?.sorted(),
        links = links?.map { it.normalised() },
    )

    private fun SpanEvent.normalised(): SpanEvent = copy(attributes = attributes?.sorted())

    private fun Link.normalised(): Link = copy(attributes = attributes?.sorted())

    private fun List<Attribute>.sorted(): List<Attribute> = sortedWith(compareBy({ it.key }, { it.data }))

    private companion object {
        const val SESSION_SPAN_NAME = "emb-session"
        const val GOLDEN_FILE = "multi_file_parity_session_part_span.json"
    }
}
