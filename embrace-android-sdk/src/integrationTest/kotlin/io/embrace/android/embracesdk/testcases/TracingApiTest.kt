package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.arch.assertIsTypePerformance
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.assertions.findCustomLinks
import io.embrace.android.embracesdk.assertions.findSpanByName
import io.embrace.android.embracesdk.assertions.hasLinkToEmbraceSpan
import io.embrace.android.embracesdk.assertions.isLinkedToSpanContext
import io.embrace.android.embracesdk.assertions.validateLinkToSpan
import io.embrace.android.embracesdk.assertions.validateLinkToSpanContext
import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeOtelJavaSpanExporter
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_VALUE
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.LinkType
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceSpanData
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.utils.compatThreadId
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTraceFlags
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTraceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class TracingApiTest {
    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private val results = mutableListOf<String>()
    private lateinit var executor: SingleThreadTestScheduledExecutor

    @Before
    fun setup() {
        executor = SingleThreadTestScheduledExecutor()
        results.clear()
    }

    @Test
    fun `check spans logged in the right session when service is initialized after a session starts`() {
        var testStartTimeMs: Long = -1
        var sessionStartTimeMs: Long = -1
        var sessionEndTimeMs: Long = -1
        val spanExporter = FakeOtelJavaSpanExporter()

        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(bgActivityCapture = true)),
            preSdkStartAction = {
                clock.tick(100L)
                embrace.addSpanExporter(spanExporter)
            },
            testCaseAction = {
                results.add(
                    "\nSpans exported before session starts: ${
                        spanExporter.exportedSpans.toList().map { it.name }
                    }"
                )
                val sessionTimeStamps = recordSession {
                    val parentSpan = checkNotNull(embrace.createSpan(name = "test-trace-root"))
                    assertTrue(parentSpan.start(startTimeMs = clock.now() + 1L))
                    assertTrue(parentSpan.addAttribute("oMg", "OmG"))
                    assertSame(parentSpan, embrace.getSpan(checkNotNull(parentSpan.spanId)))
                    assertTrue(embrace.recordSpan(name = "record-span-span", parent = parentSpan) {
                        clock.tick(100L)
                        parentSpan.addEvent("parent event")
                        parentSpan.addEvent(
                            name = "parent event with attributes and bad input time",
                            timestampMs = clock.now().millisToNanos(),
                            attributes = mapOf("key" to "value")
                        )
                        true
                    })
                    val failedOpStartTimeMs = embrace.getSdkCurrentTimeMs()
                    clock.tick(200L)
                    parentSpan.addEvent(name = "delayed event", timestampMs = clock.now() - 50L, null)
                    val failedOpEndTimeMs = embrace.getSdkCurrentTimeMs()

                    assertTrue(parentSpan.stop())

                    val attributes = mapOf(
                        Pair(TOO_LONG_ATTRIBUTE_KEY, "value"),
                        Pair("test-attr", "false"),
                        Pair("myKey", TOO_LONG_ATTRIBUTE_VALUE)
                    )

                    val events = listOf(
                        checkNotNull(
                            EmbraceSpanEvent.create(
                                name = "failure time",
                                timestampMs = failedOpEndTimeMs,
                                attributes = mapOf(
                                    Pair("retry", "1")
                                )
                            )
                        )
                    )
                    assertTrue(
                        embrace.recordCompletedSpan(
                            name = "completed-span",
                            startTimeMs = failedOpStartTimeMs.millisToNanos(),
                            endTimeMs = failedOpEndTimeMs.millisToNanos(),
                            errorCode = ErrorCode.FAILURE,
                            parent = parentSpan,
                            attributes = attributes,
                            events = events
                        )
                    )
                    val bonusSpan = checkNotNull(embrace.startSpan(name = "bonus-span", parent = parentSpan))
                    val bonusSpan2 = checkNotNull(
                        embrace.startSpan(
                            name = "bonus-span-2",
                            parent = parentSpan,
                            startTimeMs = clock.now() + 10L
                        )
                    )
                    assertTrue(bonusSpan.stop(endTimeMs = clock.now() + 1))
                    clock.tick(300L)
                    assertTrue(bonusSpan2.stop())
                    val unendingSpan = checkNotNull(embrace.startSpan("unending-span"))
                    clock.tick(100L)
                    unendingSpan.addAttribute("unending-key", "unending-value")
                    unendingSpan.addEvent("unending-event")
                    results.add(
                        "\nSpans exported before ending startup: ${
                            spanExporter.exportedSpans.toList().map { it.name }
                        }"
                    )
                }

                sessionStartTimeMs = sessionTimeStamps.startTimeMs
                testStartTimeMs = sessionTimeStamps.actionTimeMs
                sessionEndTimeMs = sessionTimeStamps.endTimeMs
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                results.add(
                    "\nSpans exported after session ends: ${
                        spanExporter.exportedSpans.toList().map { it.name }
                    }"
                )
                val allSpans = getSdkInitSpanFromBackgroundActivity() +
                    checkNotNull(session.data.spans) +
                    testRule.setup.getSpanSink().completedSpans().map(EmbraceSpanData::toEmbracePayload)

                val spansMap = allSpans.associateBy { it.name }
                val sessionSpan = checkNotNull(spansMap["emb-session"])
                val traceRootSpan = checkNotNull(spansMap["test-trace-root"])

                results.add("\nAll spans to validate: ${allSpans.map { it.name }}")
                results.add(
                    "\nSpans exported before validation: ${
                        spanExporter.exportedSpans.toList().map { it.name }
                    }"
                )
                val expectedSpanName = listOf(
                    "emb-sdk-init",
                    "test-trace-root",
                    "record-span-span",
                    "completed-span",
                    "emb-session",
                    "bonus-span",
                    "bonus-span-2",
                )
                expectedSpanName.forEach {
                    checkNotNull(spansMap[it]) { "$it not found: $results" }
                }

                assertEmbraceSpanData(
                    span = traceRootSpan,
                    expectedStartTimeMs = testStartTimeMs + 1,
                    expectedEndTimeMs = testStartTimeMs + 300,
                    expectedParentId = OtelIds.invalidSpanId,
                    expectedCustomAttributes = mapOf(Pair("oMg", "OmG")),
                    expectedEvents = listOf(
                        SpanEvent(
                            name = "parent event",
                            timestampNanos = (testStartTimeMs + 100).millisToNanos(),
                            attributes = emptyList()
                        ),
                        SpanEvent(
                            name = "parent event with attributes and bad input time",
                            timestampNanos = (testStartTimeMs + 100).millisToNanos(),
                            attributes = listOf(Attribute("key", "value"))
                        ),
                        SpanEvent(
                            name = "delayed event",
                            timestampNanos = (testStartTimeMs + 250).millisToNanos(),
                            attributes = emptyList()
                        ),
                    )
                )
                val expectedParentId = traceRootSpan.spanId
                assertEmbraceSpanData(
                    span = spansMap["record-span-span"],
                    expectedStartTimeMs = testStartTimeMs,
                    expectedEndTimeMs = testStartTimeMs + 100,
                    expectedParentId = checkNotNull(expectedParentId),
                    expectedTraceId = traceRootSpan.traceId
                )

                assertEmbraceSpanData(
                    span = spansMap["completed-span"],
                    expectedStartTimeMs = testStartTimeMs + 100,
                    expectedEndTimeMs = testStartTimeMs + 300,
                    expectedParentId = expectedParentId,
                    expectedTraceId = traceRootSpan.traceId,
                    expectedErrorCode = ErrorCode.FAILURE,
                    expectedCustomAttributes = mapOf(Pair("test-attr", "false")),
                    expectedEvents = listOf(
                        SpanEvent(
                            name = "failure time",
                            timestampNanos = (testStartTimeMs + 300).millisToNanos(),
                            attributes = listOf(Attribute("retry", "1"))
                        )
                    )
                )

                assertEmbraceSpanData(
                    span = spansMap["bonus-span"],
                    expectedStartTimeMs = testStartTimeMs + 300,
                    expectedEndTimeMs = testStartTimeMs + 301,
                    expectedParentId = expectedParentId,
                    expectedTraceId = traceRootSpan.traceId
                )

                assertEmbraceSpanData(
                    span = spansMap["bonus-span-2"],
                    expectedStartTimeMs = testStartTimeMs + 310,
                    expectedEndTimeMs = testStartTimeMs + 600,
                    expectedParentId = expectedParentId,
                    expectedTraceId = traceRootSpan.traceId
                )

                assertEmbraceSpanData(
                    span = sessionSpan,
                    expectedStartTimeMs = sessionStartTimeMs,
                    expectedEndTimeMs = sessionEndTimeMs,
                    expectedParentId = OtelIds.invalidSpanId,
                    private = false
                )

                val snapshots = checkNotNull(session.data.spanSnapshots).associateBy { it.name }
                val unendingSpanSnapshot = checkNotNull(snapshots["unending-span"])
                unendingSpanSnapshot.assertIsTypePerformance()
                assertEmbraceSpanData(
                    span = unendingSpanSnapshot,
                    expectedStartTimeMs = testStartTimeMs + 600,
                    expectedEndTimeMs = null,
                    expectedParentId = OtelIds.invalidSpanId,
                    expectedStatus = Span.Status.UNSET,
                    expectedCustomAttributes = mapOf(Pair("unending-key", "unending-value")),
                    expectedEvents = listOf(
                        SpanEvent(
                            name = "unending-event",
                            timestampNanos = (testStartTimeMs + 700).millisToNanos(),
                            attributes = emptyList()
                        )
                    )
                )
            }
        )
    }

    @Test
    fun `span can be parented by a span created on a different thread`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    val latch = CountDownLatch(1)
                    val parentThreadId = Thread.currentThread().compatThreadId()
                    var childThreadId: Long = -1L
                    val parent = checkNotNull(embrace.startSpan("parent"))
                    val currentContext = OtelJavaContext.current()
                    var currentContext2: OtelJavaContext? = null
                    executor.submit {
                        childThreadId = Thread.currentThread().compatThreadId()
                        currentContext2 = OtelJavaContext.current()
                        val child = checkNotNull(embrace.startSpan(name = "child", parent = parent))
                        assertTrue(child.stop())
                        latch.countDown()
                    }
                    latch.await(1, TimeUnit.SECONDS)
                    assertTrue(parent.stop())
                    assertNotEquals(-1L, childThreadId)
                    assertNotEquals(parentThreadId, childThreadId)
                    assertEquals(currentContext, currentContext2)
                }
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val spans = checkNotNull(session.data.spans).associateBy { it.name }
                val parentSpan = checkNotNull(spans["parent"])
                val childSpan = checkNotNull(spans["child"])
                assertEquals(parentSpan.traceId, childSpan.traceId)
                assertEquals(parentSpan.spanId, childSpan.parentSpanId)
            }
        )
    }

    @Test
    fun `can only create span if there is a valid session`() {
        testRule.runTest(
            preSdkStartAction = {
                assertNull(embrace.startSpan("test"))
            },
            testCaseAction = {
                recordSession {
                    assertNotNull(embrace.startSpan("test"))
                }
                assertNull(embrace.startSpan("test"))
                recordSession {
                    assertNotNull(embrace.startSpan("test"))
                }
            }
        )
    }

    @Test
    fun `span links`() {
        val fakeSpan = FakeEmbraceSdkSpan.started()
        val remoteSpanContext = OtelJavaSpanContext.createFromRemoteParent(
            checkNotNull(fakeSpan.traceId),
            checkNotNull(fakeSpan.spanId),
            OtelJavaTraceFlags.getDefault(),
            OtelJavaTraceState.getDefault()
        )
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    clock.tick(100L)
                    val op = checkNotNull(embrace.startSpan("my-op"))
                    clock.tick(100L)
                    val op2 = checkNotNull(embrace.startSpan("my-op-2"))
                    clock.tick(100L)
                    op2.addLink(op, mapOf("test" to "value"))
                    op2.addLink(remoteSpanContext)
                    op2.stop()
                    op.stop()
                }
            },
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    val op = findSpanByName(name = "my-op")
                    val op2 = findSpanByName(name = "my-op-2")
                    op2.hasLinkToEmbraceSpan(checkNotNull(getSessionSpan()), LinkType.EndedIn)

                    val links = checkNotNull(op2.findCustomLinks())
                    links[0].validateLinkToSpan(linkedSpan = op, expectedAttributes = mapOf("test" to "value"))
                    links[1].validateLinkToSpanContext(remoteSpanContext)
                }
            },
            otelExportAssertion = {
                val sessionSpan = awaitSpansWithType(1, EmbType.Ux.Session).single().toEmbraceSpanData().toEmbracePayload()
                val linkedToSpan = awaitSpans(1) { it.name == "my-op" }.single().toEmbraceSpanData().toEmbracePayload()
                val spanWithLinks = awaitSpans(1) { it.name == "my-op-2" }.single().toEmbraceSpanData().toEmbracePayload()
                spanWithLinks.hasLinkToEmbraceSpan(sessionSpan, LinkType.EndedIn)

                val links = checkNotNull(spanWithLinks.findCustomLinks())
                links[0].validateLinkToSpan(linkedSpan = linkedToSpan, expectedAttributes = mapOf("test" to "value"))
                assertTrue(links[1].isLinkedToSpanContext(remoteSpanContext))
            }
        )
    }

    private fun EmbracePayloadAssertionInterface.getSdkInitSpanFromBackgroundActivity(): List<Span> {
        val lastSentBackgroundActivity = getSingleSessionEnvelope(ApplicationState.BACKGROUND)
        val spans = checkNotNull(lastSentBackgroundActivity.data.spans)
        return spans.filter { it.name == "emb-sdk-init" }
    }
}
