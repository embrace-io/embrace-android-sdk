package io.embrace.android.embracesdk.testcases.features

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.PowerState.PowerMode
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.instrumentation.powersave.PowerStateDataSource
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.export.OtlpJsonHttpLogRecordExporter
import io.embrace.android.embracesdk.testframework.export.OtlpJsonHttpSender
import io.embrace.android.embracesdk.testframework.export.OtlpJsonHttpSpanExporter
import io.opentelemetry.kotlin.export.OperationResultCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A harness that runs a scripted scenario through the real SDK and sends what an app's own exporters are handed to an
 * OTLP endpoint, as OTLP over HTTP with JSON bodies.
 *
 * Every run sends it to a server of the test's own in each [Rendering], and checks what the bodies it was posted carry.
 *
 * One more test sends it to a real endpoint, so that the shape of state in OTLP can be looked at in a Collector or a
 * backend behind one. That one is skipped unless the environment names an endpoint:
 *
 * - `EMBRACE_OTLP_ENDPOINT`: where to send, such as `http://localhost:4318`. Spans go to `/v1/traces` under it and log
 *   records, the state events among them, to `/v1/logs`.
 * - `EMBRACE_STATE_EXPORT_MODE`: `ASSOCIATION`, which is the default, `VERBOSE`, `FIRST_STEP` or `FIRST_STEP_VERBOSE`. The
 *   last two are the app state data model's first step alone: a log is stamped and every change is replayed, and no span is linked.
 * - `EMBRACE_OTLP_TIME`: `fixed` starts the scenario at the fixed time tests run at, which is in 1975. Otherwise it starts
 *   at the current time, as whatever is behind the endpoint may turn old telemetry away.
 *
 * The scenario is the same every run. The app starts in the background, where no session part is in progress, as
 * background activity is not captured. It is opened on a product list, put in the background, and opened again on a
 * checkout. The network, power saving and the screen change through all of it, between the two session parts too. It
 * makes about 20 state changes, about 30 spans and about 20 logs. One span runs across a change of the network, one starts
 * in the first session part and ends in the second, and one is recorded after the fact. About half of the changes are
 * made with no session part in progress: those make no event, and each part's snapshot event has the values they left.
 *
 * Beside `network`, `power` and `screen-automatic`, one more state is in what is sent: `test`, from a data source the
 * tests' fakes register in every integration test.
 */
@RunWith(AndroidJUnit4::class)
internal class StateOtlpExportHarnessTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(OtelSdkMode.REGULAR) {
        EmbraceSetupInterface(
            workersToFake = listOf(
                Worker.Background.SessionPersistenceWorker,
                Worker.Background.PeriodicCacheWorker,
                Worker.Background.NonIoRegWorker,
                Worker.Background.IoRegWorker,
                Worker.Background.StateExportWorker,
            ),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.IoRegWorker).blockingMode = false
            // a state event is emitted as its change is reported
            getFakedWorkerExecutor(Worker.Background.StateExportWorker).blockingMode = false
            if (!System.getenv(TIME_VARIABLE).equals("fixed", ignoreCase = true)) {
                getClock().setCurrentTime(System.currentTimeMillis())
            }
        }
    }

    @Test
    fun `a scripted scenario is exported to the OTLP endpoint the environment names`() {
        val endpoint = System.getenv(ENDPOINT_VARIABLE)
        assumeTrue("Set $ENDPOINT_VARIABLE to an OTLP/HTTP endpoint to run this.", !endpoint.isNullOrBlank())
        val mode = checkNotNull(Rendering.entries.firstOrNull { it.name == (System.getenv(MODE_VARIABLE) ?: Rendering.ASSOCIATION.name) }) {
            "$MODE_VARIABLE is not one of ${Rendering.entries}"
        }
        exportScenario(checkNotNull(endpoint), mode) { sender, spanExporter, logExporter ->
            println(
                "State OTLP harness, $mode, to $endpoint: ${spanExporter.exportedSpans} spans and " +
                    "${logExporter.exportedLogRecords} log records in ${sender.acceptedRequests} accepted requests",
            )
            assertEquals("The endpoint did not accept everything it was sent", emptyList<String>(), sender.failures)
            assertTrue(spanExporter.exportedSpans.get() > 0)
            assertTrue(logExporter.exportedLogRecords.get() > 0)
        }
    }

    @Test
    fun `verbose - what is posted has the values on every linked span and on every change event`() {
        val posted = postScenario(Rendering.VERBOSE)
        posted.printChangeRecordSize(Rendering.VERBOSE)
        posted.assertSpansAndLogsAreLinked()
        val values = posted.assertEventsReplayToValues()

        // A span carries the value of every state when it started, and of each state that differed when it ended. A span
        // that started or ended with no session part in progress has a number there that no event has, and the values it
        // carries are all an app's exporter has for that number.
        val partStarts = posted.events(SNAPSHOT_EVENT).map { checkNotNull(it.long(SEQUENCE)) }.sorted()
        val lastOfFirstPart = values.keys.filter { it < partStarts[1] }.max()
        fun numberedWithNoPart(number: Long) = number < partStarts[0] || (number > lastOfFirstPart && number < partStarts[1])
        posted.linkedSpans().forEach { span ->
            val sequence = checkNotNull(span.long(SEQUENCE))
            val endSequence = span.long(END_SEQUENCE)
            listOfNotNull(sequence, endSequence).filterNot(values::containsKey).forEach { number ->
                assertTrue("${span.name()} at $number", numberedWithNoPart(number))
            }
            val atStart = values[sequence] ?: span.strings(VALUE_PREFIX)
            assertEquals(span.name(), atStart, span.strings(VALUE_PREFIX))
            if (endSequence == null) {
                assertEquals(span.name(), emptyMap<String, String>(), span.strings(END_VALUE_PREFIX))
            }
            endSequence?.let(values::get)?.let { atEnd ->
                assertEquals(span.name(), atEnd.filter { (state, value) -> atStart[state] != value }, span.strings(END_VALUE_PREFIX))
            }
        }
        // the scenario has a span of each kind: the SDK starts some before the first session part does
        assertEquals(setOf(true, false), posted.linkedSpans().map { numberedWithNoPart(checkNotNull(it.long(SEQUENCE))) }.toSet())
        // and each change event carries the value of every state after that change
        posted.events(CHANGE_EVENT).forEach { change ->
            assertEquals(values.getValue(checkNotNull(change.long(SEQUENCE))), change.strings(VALUE_PREFIX))
        }
    }

    @Test
    fun `association - what is posted has the link alone on a span, and each change alone on its event`() {
        val posted = postScenario(Rendering.ASSOCIATION)
        posted.printChangeRecordSize(Rendering.ASSOCIATION)
        posted.assertSpansAndLogsAreLinked()
        posted.assertEventsReplayToValues()

        posted.spans.forEach { span ->
            assertEquals(span.name(), emptyMap<String, String>(), span.strings(VALUE_PREFIX) + span.strings(END_VALUE_PREFIX))
        }
        posted.events(CHANGE_EVENT).forEach { change ->
            assertEquals(emptyMap<String, String>(), change.strings(VALUE_PREFIX))
        }
    }

    @Test
    fun `first step - what is posted has the stamp on a log and each change as an event, and nothing on a span`() {
        val posted = postScenario(Rendering.FIRST_STEP)
        posted.assertFirstStepOnly()

        // the changes and snapshots are replayed with the first step, each change carrying the change alone
        posted.events(CHANGE_EVENT).forEach { change ->
            assertEquals(emptyMap<String, String>(), change.strings(VALUE_PREFIX))
        }
        assertEquals(2, posted.events(SNAPSHOT_EVENT).size)
        assertTrue(posted.events(CHANGE_EVENT).isNotEmpty())
    }

    @Test
    fun `first step, verbose - a replayed change carries every value though no span carries anything`() {
        val posted = postScenario(Rendering.FIRST_STEP_VERBOSE)
        posted.assertFirstStepOnly()

        assertTrue(posted.events(CHANGE_EVENT).isNotEmpty())
        posted.events(CHANGE_EVENT).forEach { change ->
            assertTrue(change.strings(VALUE_PREFIX).keys.containsAll(listOf("network", "power", "screen-automatic")))
        }
    }

    @Test
    fun `a request the endpoint turns away is counted as failed, and a number that is not finite is posted as valid JSON`() {
        val bodies = ConcurrentLinkedQueue<String>()
        val answers = ConcurrentLinkedQueue(
            listOf(
                MockResponse().setBody("{}"),
                MockResponse().setBody("""{"partialSuccess":{"rejectedLogRecords":"1","errorMessage":"no"}}"""),
                MockResponse().setResponseCode(SERVER_ERROR),
            ),
        )
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    bodies.add(request.body.readUtf8())
                    return checkNotNull(answers.poll())
                }
            }
            start()
        }
        try {
            val sender = OtlpJsonHttpSender(server.url("/").toString())
            val exporter = OtlpJsonHttpLogRecordExporter(sender)
            val log = FakeReadWriteLogRecord(
                body = "numbers",
                attributes = mapOf(
                    "finite" to 1.5,
                    "not-a-number" to Double.NaN,
                    "infinite" to Double.POSITIVE_INFINITY,
                    "negative-infinite" to Float.NEGATIVE_INFINITY,
                ),
            )
            val results = mutableListOf<OperationResultCode>()
            runBlocking {
                repeat(answers.size) { results.add(exporter.export(listOf(log, log))) }
            }

            // only the request that was accepted whole counts as exported
            assertEquals(
                listOf(OperationResultCode.Success, OperationResultCode.Failure, OperationResultCode.Failure),
                results,
            )
            assertEquals(2, exporter.exportedLogRecords.get())
            assertEquals(4, exporter.failedLogRecords.get())
            assertEquals(1, sender.acceptedRequests.get())
            assertEquals(2, sender.failures.size)
            assertTrue(sender.failures[0], sender.failures[0].contains("accepted in part"))
            assertTrue(sender.failures[1], sender.failures[1].contains("answered $SERVER_ERROR"))

            // a number is written as one where JSON has a way to, and as a string where it has none
            val record = Json.parseToJsonElement(bodies.first()).jsonObject
                .objects("resourceLogs").single()
                .objects("scopeLogs").single()
                .objects("logRecords").first()
            val numbers = record.objects("attributes").associate { attribute ->
                attribute.text("key") to checkNotNull(attribute.getValue("value").jsonObject["doubleValue"]).jsonPrimitive
            }
            assertEquals(JsonPrimitive(1.5), numbers["finite"])
            assertEquals(JsonPrimitive("NaN"), numbers["not-a-number"])
            assertEquals(JsonPrimitive("Infinity"), numbers["infinite"])
            assertEquals(JsonPrimitive("-Infinity"), numbers["negative-infinite"])
        } finally {
            server.shutdown()
        }

        // and neither does a request that cannot be made
        val unreachable = OtlpJsonHttpSender("http://localhost:1")
        assertFalse(unreachable.post("/v1/logs", "{}"))
        assertEquals(1, unreachable.failures.size)
        assertEquals(0, unreachable.acceptedRequests.get())
    }

    /**
     * Runs the scenario with what an app's exporters are given set to [mode], sends it to [endpoint], and then
     * [afterExport] with everything posted.
     */
    private fun exportScenario(
        endpoint: String,
        mode: Rendering,
        afterExport: (OtlpJsonHttpSender, OtlpJsonHttpSpanExporter, OtlpJsonHttpLogRecordExporter) -> Unit,
    ) {
        val sender = OtlpJsonHttpSender(endpoint)
        val spanExporter = OtlpJsonHttpSpanExporter(sender)
        val logExporter = OtlpJsonHttpLogRecordExporter(sender)

        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(networkConnectivityCapture = true, powerSaveCapture = true),
            ),
            persistedRemoteConfig = RemoteConfig(
                pctMultiFilePersistenceEnabled = 100.0f,
                backgroundActivityConfig = BackgroundActivityRemoteConfig(0f),
                pctStateCaptureEnabledV2 = 100.0f,
                pctSharedStateSpanEnabled = 100.0f,
                pctStateAssociationEnabled = if (mode.secondStep) 100.0f else 0.0f,
                stateExportMode = mode.exportMode,
            ),
            preSdkStartAction = {
                embrace.addSpanExporter(spanExporter)
                embrace.addLogRecordExporter(logExporter)
            },
            testCaseAction = { runScenario() },
            assertAction = {
                // everything handed to the exporters has been posted before anything is asserted, or the test ends
                runBlocking {
                    with(testRule.services.otelSdkConfig) {
                        spanProcessor.forceFlush()
                        logRecordProcessor.forceFlush()
                    }
                }
                afterExport(sender, spanExporter, logExporter)
            },
        )
    }

    /**
     * Runs the scenario in [mode] against a server of the test's own that accepts everything, and gives what it was
     * posted, parsed. Every request has to have been counted as accepted.
     */
    private fun postScenario(mode: Rendering): Posted {
        val requests = ConcurrentLinkedQueue<Pair<String, String>>()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    assertEquals("application/json", request.getHeader("Content-Type"))
                    requests.add(checkNotNull(request.path) to request.body.readUtf8())
                    return MockResponse().setBody("{}")
                }
            }
            start()
        }
        try {
            exportScenario(server.url("/").toString(), mode) { sender, spanExporter, logExporter ->
                assertEquals(emptyList<String>(), sender.failures)
                assertEquals(requests.size, sender.acceptedRequests.get())
                assertEquals(0, spanExporter.failedSpans.get() + logExporter.failedLogRecords.get())
            }
        } finally {
            server.shutdown()
        }
        val traces = requests.filter { it.first == "/v1/traces" }.map { Json.parseToJsonElement(it.second).jsonObject }
        val logs = requests.filter { it.first == "/v1/logs" }.map { Json.parseToJsonElement(it.second).jsonObject }
        assertEquals(requests.size, traces.size + logs.size)
        val resourceSpans = traces.flatMap { it.objects("resourceSpans") }
        val resourceLogs = logs.flatMap { it.objects("resourceLogs") }
        return Posted(
            spans = resourceSpans.flatMap { it.objects("scopeSpans") }.flatMap { it.objects("spans") },
            logRecords = resourceLogs.flatMap { it.objects("scopeLogs") }.flatMap { it.objects("logRecords") },
            resources = (resourceSpans + resourceLogs).map { it.getValue("resource").jsonObject },
        )
    }

    /**
     * Whatever the rendering: a span that ran across a change carries both numbers as whole numbers, one that no state
     * changed during carries one, the span recorded once it was over is linked too, every span says what its trace flags
     * are, and a log carries the number and one attribute for each state.
     */
    private fun Posted.assertSpansAndLogsAreLinked() {
        spans.forEach { span ->
            assertEquals(span.name(), TRACE_ID_LENGTH, span.text("traceId").length)
            assertEquals(span.name(), SPAN_ID_LENGTH, span.text("spanId").length)
            // a span the SDK records is sampled, and its parent is known not to be of another process
            val flags = span.getValue("flags").jsonPrimitive.content.toInt()
            assertEquals(span.name(), FLAG_SAMPLED or FLAG_HAS_IS_REMOTE, flags and (FLAG_SAMPLED or FLAG_HAS_IS_REMOTE or FLAG_IS_REMOTE))
        }
        // the network changed while the photo uploaded
        val upload = spans.single { it.name() == "upload-review-photo" }
        assertEquals(checkNotNull(upload.long(SEQUENCE)) + 1, upload.long(END_SEQUENCE))

        // power saving came on before the item was added to the cart, and nothing changed while it was
        val steady = spans.single { it.name() == "add-to-cart" }
        assertEquals(checkNotNull(upload.long(END_SEQUENCE)) + 1, steady.long(SEQUENCE))
        assertNull(steady.attribute(END_SEQUENCE))

        // Recorded once it was over, after the item was added: it started before power saving came on and ended after.
        // Its numbers are the ones that held at those times, and not the one that held when it was recorded.
        val recorded = spans.single { it.name() == "decode-product-image" }
        assertEquals(upload.long(END_SEQUENCE), recorded.long(SEQUENCE))
        assertEquals(steady.long(SEQUENCE), recorded.long(END_SEQUENCE))

        // started in the first session part and ended in the second, with many changes in between
        val checkout = spans.single { it.name() == "checkout-flow" }
        assertTrue(checkNotNull(checkout.long(END_SEQUENCE)) > checkNotNull(checkout.long(SEQUENCE)) + 1)

        val log = logRecords.single { it.body() == "Review photo upload took longer than expected" }
        assertEquals(upload.long(END_SEQUENCE), log.long(SEQUENCE))
        assertTrue(log.strings(VALUE_PREFIX).keys.containsAll(listOf("network", "screen-automatic")))
        resources.forEach { resource -> assertNotNull(resource.attribute("service.name")) }
    }

    /**
     * What the first step alone gives, whatever the rendering: no span has a number or a value, a log has the number and one
     * attribute for each state, and every event and the Resource say which process the states belong to.
     */
    private fun Posted.assertFirstStepOnly() {
        spans.forEach { span ->
            assertNull(span.name(), span.attribute(SEQUENCE))
            assertNull(span.name(), span.attribute(END_SEQUENCE))
            assertEquals(span.name(), emptyMap<String, String>(), span.strings(VALUE_PREFIX) + span.strings(END_VALUE_PREFIX))
        }
        val log = logRecords.single { it.body() == "Review photo upload took longer than expected" }
        assertNotNull(log.long(SEQUENCE))
        assertTrue(log.strings(VALUE_PREFIX).keys.containsAll(listOf("network", "screen-automatic")))
        val processIdentifier = checkNotNull(resources.first().string(PROCESS_IDENTIFIER))
        resources.forEach { resource -> assertEquals(processIdentifier, resource.string(PROCESS_IDENTIFIER)) }
        (events(CHANGE_EVENT) + events(SNAPSHOT_EVENT)).forEach { event ->
            assertEquals(processIdentifier, event.string(ENTITY_ID))
        }
        assertEquals(logRecords.count { it["eventName"] != null }, events(CHANGE_EVENT).size + events(SNAPSHOT_EVENT).size)
    }

    /**
     * The events are the value of every state when each of the two session parts started, and every change made during a
     * session part, each once and in order. A change made with no session part in progress has no event, and the next
     * snapshot has the value it left. Replaying each part's changes from its snapshot gives the value of every state as of
     * each number an event has, which is what this returns, and what a log and, in the verbose rendering, a span and a
     * change event say they were.
     */
    private fun Posted.assertEventsReplayToValues(): Map<Long, Map<String, String>> {
        val changes = events(CHANGE_EVENT).sortedBy { it.long(SEQUENCE) }
        val snapshots = events(SNAPSHOT_EVENT).sortedBy { it.long(SEQUENCE) }
        assertEquals(2, snapshots.size)
        val (firstStart, secondStart) = snapshots.map { checkNotNull(it.long(SEQUENCE)) }
        val processIdentifier = checkNotNull(changes.first().string(ENTITY_ID))
        val values = LinkedHashMap<Long, Map<String, String>>()
        var current = emptyMap<String, String>()
        var last = 0L
        changes.forEachIndexed { index, change ->
            val sequence = checkNotNull(change.long(SEQUENCE))
            // the changes of a part follow its snapshot, and no limit refused one, so their numbers run on from it
            val startOfPart = if (sequence > secondStart) secondStart else firstStart
            if (last < startOfPart) {
                current = snapshots[if (startOfPart == firstStart) 0 else 1].strings(VALUE_PREFIX)
                values[startOfPart] = current
                last = startOfPart
            }
            assertEquals("change $index", last + 1, sequence)
            last = sequence
            val state = checkNotNull(change.string("emb.app.state.name"))
            current = current + (state to checkNotNull(change.string("emb.app.state.new_value")))
            values[sequence] = current
            assertEquals("process", change.string("emb.entity.type"))
            assertEquals(processIdentifier, change.string(ENTITY_ID))
            assertNull(change["body"])
        }

        // The network changed before the first part, and power saving and the network between the two: each took a number
        // and has no event, and the snapshot that follows has the value it left.
        assertTrue(firstStart >= 1)
        assertEquals("wifi", snapshots[0].strings(VALUE_PREFIX)["network"])
        val lastOfFirstPart = changes.map { checkNotNull(it.long(SEQUENCE)) }.last { it <= secondStart }
        assertTrue(secondStart >= lastOfFirstPart + 2)
        with(snapshots[1].strings(VALUE_PREFIX)) {
            assertEquals("wifi", get("network"))
            assertEquals("normal", get("power"))
            assertNotEquals(values.getValue(lastOfFirstPart)["power"], get("power"))
        }
        snapshots.forEach { snapshot -> assertEquals(processIdentifier, snapshot.string(ENTITY_ID)) }
        assertEquals(changes.size + snapshots.size, logRecords.count { it["eventName"] != null })

        logRecords.filter { it["eventName"] == null }.forEach { log ->
            assertEquals(log.body(), values.getValue(checkNotNull(log.long(SEQUENCE))), log.strings(VALUE_PREFIX))
        }
        // the Resource says which process the states of the events belong to
        resources.forEach { resource -> assertEquals(processIdentifier, resource.string(PROCESS_IDENTIFIER)) }

        // the network changed while the photo uploaded, which is what the two numbers on its span resolve to
        val upload = spans.single { it.name() == "upload-review-photo" }
        assertNotEquals(
            values.getValue(checkNotNull(upload.long(SEQUENCE)))["network"],
            values.getValue(checkNotNull(upload.long(END_SEQUENCE)))["network"],
        )
        return values
    }

    private fun EmbraceActionInterface.runScenario() {
        // in the background, with no session part in progress
        network(ConnectivityStatus.Wifi(true))
        lateinit var checkout: EmbraceSpan

        recordSession(activityClass = ProductListActivity::class.java) {
            checkout = embrace.startSpan("checkout-flow")
            repeat(PAGES) { page ->
                span("load-products-page-$page", durationMs = 180)
                embrace.logInfo("Product page $page rendered")
            }
            // the network drops from wifi to mobile data while a photo uploads
            val upload = embrace.startSpan("upload-review-photo")
            clock.tick(400)
            network(ConnectivityStatus.Wan(true))
            clock.tick(900)
            upload.stop()
            embrace.logWarning("Review photo upload took longer than expected")

            power(PowerMode.LOW)
            span("add-to-cart", durationMs = 60)
            embrace.logInfo("Item added to cart")
            // a span recorded once it is over, with the times it is given
            embrace.recordCompletedSpan("decode-product-image", clock.now() - 350, clock.now() - 50)
            network(ConnectivityStatus.None)
            span("refresh-cart", durationMs = 2000)
            embrace.logError("Cart refresh failed: no connection")
            network(ConnectivityStatus.Wan(true))
            span("refresh-cart", durationMs = 240)
            embrace.logInfo("Cart refreshed")
            span("render-cart-badge", durationMs = 15)
        }

        // in the background again: no session part is in progress while these change
        clock.tick(60_000)
        power(PowerMode.NORMAL)
        clock.tick(30_000)
        network(ConnectivityStatus.Wifi(true))
        clock.tick(30_000)

        recordSession(activityClass = CheckoutActivity::class.java) {
            repeat(PAGES) { step ->
                span("load-checkout-step-$step", durationMs = 120)
                embrace.logInfo("Checkout step $step shown")
            }
            span("validate-address", durationMs = 300)
            network(ConnectivityStatus.None)
            embrace.logWarning("Connection lost during checkout")
            span("retry-payment-token", durationMs = 1500)
            network(ConnectivityStatus.Wifi(true))
            embrace.logInfo("Connection restored")
            power(PowerMode.LOW)
            span("submit-order", durationMs = 800)
            embrace.logInfo("Order submitted")
            span("load-confirmation", durationMs = 200)
            power(PowerMode.NORMAL)
            embrace.logInfo("Confirmation shown")
            // started in the first session part
            checkout.stop()
            embrace.logInfo("Checkout flow finished")
        }
    }

    private fun EmbraceActionInterface.span(name: String, durationMs: Long) {
        val span = embrace.startSpan(name)
        clock.tick(durationMs)
        span.stop()
        clock.tick(GAP_MS)
    }

    private fun EmbraceActionInterface.network(status: ConnectivityStatus) {
        clock.tick(GAP_MS)
        simulateConnectivityChange(status)
    }

    private fun EmbraceActionInterface.power(mode: PowerMode) {
        clock.tick(GAP_MS)
        findDataSource<PowerStateDataSource>().onStateChange(mode, clock.now())
    }

    /**
     * What a server was posted, as the JSON objects of OTLP: every span, every log record, and the Resource of each
     * group of them.
     */
    private class Posted(
        val spans: List<JsonObject>,
        val logRecords: List<JsonObject>,
        val resources: List<JsonObject>,
    ) {
        fun events(name: String): List<JsonObject> = logRecords.filter { it["eventName"]?.jsonPrimitive?.content == name }

        /**
         * Prints the size of the JSON of a change record, where the environment names an endpoint: of the first one with no
         * previous value, which is a state's first change, and of the last one with a previous value. Every state of the
         * scenario has its first value before the first session part, and a change made with no session part has no
         * event, so the scenario has no record of the first kind and says so.
         */
        fun printChangeRecordSize(mode: Rendering) {
            // printed only where the harness is run for its numbers, as the endpoint test is
            if (System.getenv(ENDPOINT_VARIABLE).isNullOrBlank()) {
                return
            }
            val (later, first) = events(CHANGE_EVENT).partition { it.string(PREVIOUS_VALUE) != null }
            val firstSize = first.firstOrNull()?.toString()?.length ?: "none in this scenario"
            val laterSize = later.lastOrNull()?.toString()?.length ?: "none in this scenario"
            println(
                "State OTLP harness, $mode: a change record with no previous value is $firstSize bytes of JSON, " +
                    "and one with a previous value is $laterSize",
            )
        }

        fun linkedSpans(): List<JsonObject> = spans.filter { it.attribute(SEQUENCE) != null }
    }

    /**
     * How the app state data model is switched for a run, and so what an app's exporters are given.
     */
    private enum class Rendering(val secondStep: Boolean, val exportMode: String?) {
        /** The first step alone: changes are replayed, with the change alone on an event, and no span carries anything. */
        FIRST_STEP(secondStep = false, exportMode = null),

        /** The first step alone, with every value on a replayed change too. */
        FIRST_STEP_VERBOSE(secondStep = false, exportMode = "verbose"),

        /** Both steps, with a span carrying the two numbers alone and a change event the change alone. */
        ASSOCIATION(secondStep = true, exportMode = "association"),

        /** Both steps, with the values on a span and on a change event too. */
        VERBOSE(secondStep = true, exportMode = "verbose"),
    }

    private companion object {
        const val ENDPOINT_VARIABLE = "EMBRACE_OTLP_ENDPOINT"
        const val MODE_VARIABLE = "EMBRACE_STATE_EXPORT_MODE"
        const val TIME_VARIABLE = "EMBRACE_OTLP_TIME"
        const val PAGES = 5
        const val GAP_MS = 25L

        const val SEQUENCE = "emb.app.state.seq"
        const val END_SEQUENCE = "emb.app.state.end_seq"
        const val VALUE_PREFIX = "emb.app.state.value."
        const val END_VALUE_PREFIX = "emb.app.state.end_value."
        const val CHANGE_EVENT = "emb.app.state.change"
        const val SNAPSHOT_EVENT = "emb.app.state.snapshot"
        const val PREVIOUS_VALUE = "emb.app.state.previous_value"
        const val ENTITY_ID = "emb.entity.id"
        const val PROCESS_IDENTIFIER = "emb.process_identifier"
        const val TRACE_ID_LENGTH = 32
        const val SPAN_ID_LENGTH = 16
        const val FLAG_SAMPLED = 0x01
        const val FLAG_HAS_IS_REMOTE = 0x100
        const val FLAG_IS_REMOTE = 0x200
        const val SERVER_ERROR = 500

        fun JsonObject.objects(name: String): List<JsonObject> = this[name]?.jsonArray.orEmpty().map { it.jsonObject }

        fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content

        fun JsonObject.name(): String = text("name")

        fun JsonObject.body(): String? = this["body"]?.jsonObject?.text("stringValue")

        /**
         * The value of the attribute named [key] of this span, log record or Resource, as the one field of OTLP's
         * `AnyValue` it is written as.
         */
        fun JsonObject.attribute(key: String): JsonObject? =
            objects("attributes").singleOrNull { it.text("key") == key }?.getValue("value")?.jsonObject

        /**
         * A whole number, which OTLP's JSON writes as a string in `intValue`. Anything else under [key] fails.
         */
        fun JsonObject.long(key: String): Long? = attribute(key)?.let { value ->
            val number = value.getValue("intValue").jsonPrimitive
            assertTrue("$key is not written as a string", number.isString)
            number.content.toLong()
        }

        fun JsonObject.string(key: String): String? = attribute(key)?.text("stringValue")

        /**
         * The text attributes whose names begin with [prefix], by the rest of their names.
         */
        fun JsonObject.strings(prefix: String): Map<String, String> = objects("attributes")
            .filter { it.text("key").startsWith(prefix) }
            .associate { it.text("key").removePrefix(prefix) to it.getValue("value").jsonObject.text("stringValue") }
    }
}

/**
 * The screens of the scenario. The screen state takes an activity's class name as its value.
 */
internal class ProductListActivity : Activity()

internal class CheckoutActivity : Activity()
