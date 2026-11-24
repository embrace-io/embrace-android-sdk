package io.embrace.android.embracesdk.testframework.export

import io.opentelemetry.kotlin.InstrumentationScopeInfo
import io.opentelemetry.kotlin.attributes.AnyValue
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.resource.Resource
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Posts OTLP requests with JSON bodies to an OTLP/HTTP endpoint, such as the `otlp` receiver of an OpenTelemetry
 * Collector, and remembers every request the endpoint did not take whole.
 *
 * Test-only: no OTLP exporter is on the classpath, so the two exporters below encode the protocol by hand.
 */
internal class OtlpJsonHttpSender(endpoint: String) {

    private val baseUrl = endpoint.trimEnd('/')

    /**
     * What went wrong with each request that was not accepted whole: the endpoint could not be reached, answered with
     * anything but success, or said it rejected part of what it was sent.
     */
    val failures: MutableList<String> = CopyOnWriteArrayList()

    val acceptedRequests = AtomicInteger()

    /**
     * Posts [body] to [path] under the endpoint, and says whether the endpoint accepted all of it.
     */
    fun post(path: String, body: String): Boolean {
        return try {
            val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in SUCCESS) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                .orEmpty()
            val rejected = REJECTED.find(response)?.groupValues?.get(1)?.toLongOrNull() ?: 0
            when {
                status !in SUCCESS -> failures.add("POST $path answered $status: $response")
                rejected > 0 -> failures.add("POST $path was accepted in part: $response")
                else -> acceptedRequests.incrementAndGet()
            }
            status in SUCCESS && rejected == 0L
        } catch (exc: Exception) {
            // whatever keeps a request from being made or answered, it was not accepted
            failures.add("POST $path failed: $exc")
            false
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        val SUCCESS = 200..299
        val REJECTED = Regex("\"rejected(?:Spans|LogRecords)\"\\s*:\\s*\"?(\\d+)")
    }
}

/**
 * Exports spans as `ExportTraceServiceRequest` JSON to `/v1/traces`.
 */
internal class OtlpJsonHttpSpanExporter(private val sender: OtlpJsonHttpSender) : SpanExporter {

    /**
     * How many spans were in requests the endpoint accepted whole.
     */
    val exportedSpans = AtomicInteger()

    /**
     * How many spans were in requests it did not.
     */
    val failedSpans = AtomicInteger()

    override suspend fun export(telemetry: List<SpanData>): OperationResultCode {
        if (telemetry.isEmpty()) {
            return OperationResultCode.Success
        }
        val body = jsonObject {
            array("resourceSpans", telemetry.groupBy { it.resource }.entries) { (resource, ofResource) ->
                resource(resource)
                array("scopeSpans", ofResource.groupBy { it.instrumentationScopeInfo }.entries) { (scope, ofScope) ->
                    scope(scope)
                    array("spans", ofScope) { span -> span(span) }
                }
            }
        }
        return if (sender.post("/v1/traces", body)) {
            exportedSpans.addAndGet(telemetry.size)
            OperationResultCode.Success
        } else {
            failedSpans.addAndGet(telemetry.size)
            OperationResultCode.Failure
        }
    }

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success

    private fun JsonObject.span(span: SpanData) {
        string("traceId", span.spanContext.traceId)
        string("spanId", span.spanContext.spanId)
        if (span.parent.isValid) {
            string("parentSpanId", span.parent.spanId)
        }
        // the span's own trace flags, and whether its parent is in another process
        number("flags", span.spanContext.traceFlagBits() or span.parent.remoteBits())
        string("name", span.name)
        number("kind", span.spanKind.otlpNumber())
        string("startTimeUnixNano", span.startTimestamp.toString())
        string("endTimeUnixNano", (span.endTimestamp ?: span.startTimestamp).toString())
        attributes(span.attributes)
        number("droppedAttributesCount", span.droppedAttributesCount)
        array("events", span.events) { event ->
            string("timeUnixNano", event.timestamp.toString())
            string("name", event.name)
            attributes(event.attributes)
        }
        number("droppedEventsCount", span.droppedEventsCount)
        array("links", span.links) { link ->
            string("traceId", link.spanContext.traceId)
            string("spanId", link.spanContext.spanId)
            attributes(link.attributes)
            number("flags", link.spanContext.traceFlagBits() or link.spanContext.remoteBits())
        }
        number("droppedLinksCount", span.droppedLinksCount)
        obj("status") {
            span.status.description?.let { string("message", it) }
            number(
                "code",
                when (span.status.statusCode) {
                    StatusCode.UNSET -> 0
                    StatusCode.OK -> 1
                    StatusCode.ERROR -> 2
                },
            )
        }
    }

    private fun SpanKind.otlpNumber(): Int = when (this) {
        SpanKind.INTERNAL -> 1
        SpanKind.SERVER -> 2
        SpanKind.CLIENT -> 3
        SpanKind.PRODUCER -> 4
        SpanKind.CONSUMER -> 5
    }
}

/**
 * Exports log records, events among them, as `ExportLogsServiceRequest` JSON to `/v1/logs`.
 */
internal class OtlpJsonHttpLogRecordExporter(private val sender: OtlpJsonHttpSender) : LogRecordExporter {

    /**
     * How many log records were in requests the endpoint accepted whole.
     */
    val exportedLogRecords = AtomicInteger()

    /**
     * How many log records were in requests it did not.
     */
    val failedLogRecords = AtomicInteger()

    override suspend fun export(telemetry: List<LogRecordData>): OperationResultCode {
        if (telemetry.isEmpty()) {
            return OperationResultCode.Success
        }
        val body = jsonObject {
            array("resourceLogs", telemetry.groupBy { it.resource }.entries) { (resource, ofResource) ->
                resource(resource)
                array("scopeLogs", ofResource.groupBy { it.instrumentationScopeInfo }.entries) { (scope, ofScope) ->
                    scope(scope)
                    array("logRecords", ofScope) { log -> logRecord(log) }
                }
            }
        }
        return if (sender.post("/v1/logs", body)) {
            exportedLogRecords.addAndGet(telemetry.size)
            OperationResultCode.Success
        } else {
            failedLogRecords.addAndGet(telemetry.size)
            OperationResultCode.Failure
        }
    }

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success

    private fun JsonObject.logRecord(log: LogRecordData) {
        log.timestamp?.let { string("timeUnixNano", it.toString()) }
        log.observedTimestamp?.let { string("observedTimeUnixNano", it.toString()) }
        log.severityNumber?.let { number("severityNumber", it.severityNumber) }
        log.severityText?.let { string("severityText", it) }
        log.eventName?.let { string("eventName", it) }
        log.body?.let { body -> obj("body") { anyValue(body) } }
        attributes(log.attributes)
        number("droppedAttributesCount", log.droppedAttributesCount)
        if (log.spanContext.isValid) {
            number("flags", log.spanContext.traceFlagBits())
            string("traceId", log.spanContext.traceId)
            string("spanId", log.spanContext.spanId)
        }
    }
}

/**
 * The W3C trace flags of this context, as the low byte of an OTLP `flags` field.
 */
private fun SpanContext.traceFlagBits(): Int =
    (if (traceFlags.isSampled) FLAG_SAMPLED else 0) or (if (traceFlags.isRandom) FLAG_RANDOM else 0)

/**
 * Whether this context is from another process, as the two bits of an OTLP `flags` field that say so of a span's parent
 * or of the span a link is to: one that says it is known, and one that says it is.
 */
private fun SpanContext.remoteBits(): Int = FLAG_HAS_IS_REMOTE or (if (isRemote) FLAG_IS_REMOTE else 0)

private const val FLAG_SAMPLED = 0x01
private const val FLAG_RANDOM = 0x02
private const val FLAG_HAS_IS_REMOTE = 0x100
private const val FLAG_IS_REMOTE = 0x200

private fun JsonObject.resource(resource: Resource) {
    obj("resource") { attributes(resource.attributes) }
    resource.schemaUrl?.let { string("schemaUrl", it) }
}

private fun JsonObject.scope(scope: InstrumentationScopeInfo) {
    obj("scope") {
        string("name", scope.name)
        scope.version?.let { string("version", it) }
        attributes(scope.attributes)
    }
    scope.schemaUrl?.let { string("schemaUrl", it) }
}

private fun JsonObject.attributes(attributes: Map<String, Any>) {
    array("attributes", attributes.entries) { (key, value) ->
        string("key", key)
        obj("value") { anyValue(value) }
    }
}

/**
 * Writes [value] as the one field of an OTLP `AnyValue`. A whole number is a string in OTLP's JSON, as every 64-bit
 * integer is. So is a number that is not finite, which JSON has no number for: `"NaN"`, `"Infinity"` or `"-Infinity"`.
 */
private fun JsonObject.anyValue(value: Any?) {
    when (value) {
        null, AnyValue.NullValue -> Unit
        is String -> string("stringValue", value)
        is Boolean -> raw("boolValue", value.toString())
        is Long, is Int -> string("intValue", value.toString())
        is Double -> double("doubleValue", value)
        is Float -> double("doubleValue", value.toDouble())
        is ByteArray -> string("bytesValue", java.util.Base64.getEncoder().encodeToString(value))
        is List<*> -> obj("arrayValue") { array("values", value) { element -> anyValue(element) } }
        is Map<*, *> -> obj("kvlistValue") {
            array("values", value.entries) { (key, element) ->
                string("key", key.toString())
                obj("value") { anyValue(element) }
            }
        }
        is AnyValue.StringValue -> string("stringValue", value.value)
        is AnyValue.BoolValue -> raw("boolValue", value.value.toString())
        is AnyValue.LongValue -> string("intValue", value.value.toString())
        is AnyValue.DoubleValue -> double("doubleValue", value.value)
        is AnyValue.BytesValue -> string("bytesValue", java.util.Base64.getEncoder().encodeToString(value.value))
        is AnyValue.ListValue -> anyValue(value.values)
        is AnyValue.MapValue -> anyValue(value.values)
        else -> string("stringValue", value.toString())
    }
}

private fun jsonObject(content: JsonObject.() -> Unit): String = StringBuilder().also { JsonObject(it).write(content) }.toString()

/**
 * Writes the fields of one JSON object, in the order they are given.
 */
private class JsonObject(private val out: StringBuilder) {

    private var fields = 0

    fun write(content: JsonObject.() -> Unit) {
        out.append('{')
        content()
        out.append('}')
    }

    fun string(name: String, value: String) {
        name(name)
        quote(value)
    }

    fun number(name: String, value: Int) = raw(name, value.toString())

    fun double(name: String, value: Double) {
        if (value.isNaN() || value.isInfinite()) {
            string(name, value.toString())
        } else {
            raw(name, value.toString())
        }
    }

    fun raw(name: String, json: String) {
        name(name)
        out.append(json)
    }

    fun obj(name: String, content: JsonObject.() -> Unit) {
        name(name)
        JsonObject(out).write(content)
    }

    fun <T> array(name: String, elements: Iterable<T>, content: JsonObject.(T) -> Unit) {
        name(name)
        out.append('[')
        elements.forEachIndexed { index, element ->
            if (index > 0) {
                out.append(',')
            }
            JsonObject(out).write { content(element) }
        }
        out.append(']')
    }

    private fun name(name: String) {
        if (fields++ > 0) {
            out.append(',')
        }
        quote(name)
        out.append(':')
    }

    private fun quote(text: String) {
        out.append('"')
        for (char in text) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char == '\n' -> out.append("\\n")
                char == '\r' -> out.append("\\r")
                char == '\t' -> out.append("\\t")
                char < ' ' -> out.append("\\u").append(char.code.toString(HEX).padStart(UNICODE_DIGITS, '0'))
                else -> out.append(char)
            }
        }
        out.append('"')
    }

    private companion object {
        const val HEX = 16
        const val UNICODE_DIGITS = 4
    }
}
