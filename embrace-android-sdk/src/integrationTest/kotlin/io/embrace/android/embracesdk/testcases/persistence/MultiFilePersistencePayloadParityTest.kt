package io.embrace.android.embracesdk.testcases.persistence

import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.assertions.findEventsOfType
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.findSpanByName
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.session.getSessionProperty
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.EmbSpanAttributes
import io.embrace.android.embracesdk.testcases.features.createNativeSymbolsForCurrentArch
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Parity of what a single session part carries: its spans, events, properties and envelope.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class MultiFilePersistencePayloadParityTest(
    persistenceMode: PersistenceMode,
    otelSdkMode: OtelSdkMode,
) : MultiFilePersistenceParityTestBase(persistenceMode, otelSdkMode) {

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

    /**
     * The harness never runs the debounced metadata write, so the change can only reach the payload
     * through what each mode does at part end: the single-file mode builds the payload from live state,
     * the multi-file mode flushes its pending writes.
     */
    @Test
    fun `user info changed mid-session is persisted identically`() {
        testRule.runTest(
            instrumentedConfig = hostedSdkConfig,
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                changeEnvelopeMetadata(BEFORE)
                recordSession {
                    changeEnvelopeMetadata(AFTER)
                }
            },
            assertAction = {
                deliveredParts().single().assertEnvelopeMetadata(AFTER)
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
    fun `a periodic cache tick before the session ends does not change what is persisted`() {
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
    fun `the session span's events and links survive being persisted`() {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession {
                    embrace.recordSpan("linked-span") {
                        clock.tick(100)
                    }
                    clock.tick(WRITE_DEBOUNCE_WAIT_MS * 2)
                    embrace.addBreadcrumb("last gasp")
                }
            },
            assertAction = {
                val sessionSpan = deliveredParts().single().findSessionPartSpan()
                assertEquals(
                    "the session span lost its events",
                    1,
                    sessionSpan.findEventsOfType(EmbType.System.Breadcrumb).size,
                )
                assertTrue(
                    "the session span lost its links: ${sessionSpan.links}",
                    sessionSpan.links.orEmpty().any {
                        it.attributes?.findAttributeValue(EmbSpanAttributes.EMB_LINK_TYPE) == "ENDED_IN"
                    },
                )
            },
        )
    }

    private companion object {
        const val WRITE_DEBOUNCE_WAIT_MS = 1000L
    }
}
