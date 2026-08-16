@file:OptIn(ExperimentalApi::class)

package io.embrace.android.exampleapp

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.provider.Settings
import android.util.Log
import io.embrace.android.embracesdk.Embrace
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.context.Context as OtelContext
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord
import io.opentelemetry.kotlin.resource.Resource
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mirrors SDK telemetry to logcat as machine-readable JSON so that a host-side script can verify
 * SDK telemetry with a single `adb logcat -d -v raw -s EmbVerify:I`, on any build type (including
 * non-debuggable benchmark builds where `run-as` cache pulls fail).
 *
 * Registered as OTel span/log processors rather than exporters because processors also see spans
 * marked private (e.g. `emb-sdk-init`), which the SDK filters out of user-registered exporters.
 * Implemented against the opentelemetry-kotlin processor API, which is engine-mode-agnostic:
 * externally-registered kotlin processors are composed directly after Embrace's own processor in
 * both engine modes. With the default java-backed engine they are attached to the java SDK via
 * `OtelJavaSpanProcessorAdapter`, whose `onEnd` unconditionally wraps every java span, and with
 * `sdk_config.otel.enable_otel_kotlin_sdk = true` they are invoked natively — unlike Java-typed
 * processors added via `addJavaSpanProcessor`, which go silent in kotlin-SDK mode.
 *
 * Modes, selected by the device-global setting `embrace_verify_telemetry`
 * (`adb shell settings put global embrace_verify_telemetry <mode>`); cost model per mode:
 *
 * - unset/other: nothing is registered — zero overhead beyond this one settings read.
 * - `startup` (or legacy `1`): startup-scoped low-perturbation mode. Only spans in
 *   [STARTUP_SPAN_NAMES] are captured; every other span end pays a name check against a small
 *   set — no allocation, no serialization, no logging. Matching spans have their immutable
 *   [SpanData] snapshot queued at `onEnd` (snapshots are safe to serialize late: the compat
 *   `SpanDataAdapter` eagerly copies every field at construction, and the kotlin-SDK
 *   `toSpanData()` contract requires an immutable instance — and the span has already ended).
 *   A single flush pass on a dedicated low-priority handler thread serializes and emits the
 *   queue [STARTUP_FLUSH_DELAY_MS] after registration — outside the startup window being
 *   measured — then emits a `{"kind":"flush","mode":"startup","count":N}` marker line. Matching
 *   spans that end after the flush (e.g. warm-startup traces) emit immediately at `onEnd`.
 *   No log-record processor is registered in this mode.
 * - `all`: emits every span at `onEnd` and every log at emit time, serialized inline on the
 *   producing thread. Full fidelity (session part span included, once it ends), but NOT for
 *   perf-sensitive runs.
 *
 * Host recipe: set the mode, `adb logcat -c`, launch, then read `adb logcat -d -v raw -s
 * EmbVerify:I`. In `startup` mode wait for the flush marker line (~12 s after launch) instead of
 * guessing timing; in `all` mode background the app first to force the session part span to end.
 *
 * Output format: each telemetry item is one JSON object (`kind` = `resource`|`span`|`log`|`flush`),
 * split into logcat lines of the form `EMBV1 <seq> <chunkIndex>/<chunkCount> <fragment>` to stay
 * under logcat's ~4 KB per-line payload limit. Hosts group lines by `<seq>`, concatenate the
 * fragments in order, and parse the result as JSON.
 */
object TelemetryVerificationTap {

    /**
     * Registers the tap's processors if verification is enabled for this run. Must be called
     * before [Embrace.start], since the SDK ignores processors added after it has started.
     */
    fun registerIfEnabled(context: Context) {
        val mode = when (Settings.Global.getString(context.contentResolver, SETTING_KEY)) {
            "all" -> Mode.ALL
            "startup", "1" -> Mode.STARTUP
            else -> null
        } ?: return
        Embrace.addSpanProcessor(VerificationSpanProcessor(mode))
        if (mode == Mode.ALL) {
            Embrace.addLogRecordProcessor(VerificationLogRecordProcessor())
        } else {
            scheduleStartupFlush()
        }
    }

    private enum class Mode { STARTUP, ALL }

    private class VerificationSpanProcessor(private val mode: Mode) : SpanProcessor {
        override fun onStart(span: ReadWriteSpan, parentContext: OtelContext) {
        }

        override fun onEnding(span: ReadWriteSpan) {
        }

        override fun onEnd(span: ReadableSpan) {
            if (mode == Mode.STARTUP && span.name !in STARTUP_SPAN_NAMES) {
                return
            }
            runCatching {
                val data = span.toSpanData()
                if (mode == Mode.ALL || startupFlushDone.get()) {
                    emitSpan(data)
                } else {
                    pendingStartupSpans.add(data)
                    // closes the race with the flush pass: if the flush completed while this
                    // span was being queued, take it back and emit it inline instead
                    if (startupFlushDone.get() && pendingStartupSpans.remove(data)) {
                        emitSpan(data)
                    }
                }
            }
        }

        override fun isStartRequired(): Boolean = false

        override fun isEndRequired(): Boolean = true

        override fun isOnEndingRequired(): Boolean = false

        override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success

        override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
    }

    private class VerificationLogRecordProcessor : LogRecordProcessor {
        override fun onEmit(log: ReadWriteLogRecord, context: OtelContext) {
            runCatching {
                emit(log.toJson())
            }
        }

        override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success

        override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
    }

    private fun scheduleStartupFlush() {
        val thread = HandlerThread("emb-verify-flush", Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        Handler(thread.looper).postDelayed(
            {
                flushStartupSpans()
                thread.quitSafely()
            },
            STARTUP_FLUSH_DELAY_MS,
        )
    }

    private fun flushStartupSpans() {
        startupFlushDone.set(true)
        var count = 0
        while (true) {
            val data = pendingStartupSpans.poll() ?: break
            runCatching {
                emitSpan(data)
                count++
            }
        }
        emit(
            JSONObject().apply {
                put("kind", "flush")
                put("mode", "startup")
                put("count", count)
            },
        )
    }

    private fun emitSpan(data: SpanData) {
        emitResourceOnce(data.resource)
        emit(data.toJson())
    }

    private fun SpanData.toJson(): JSONObject = JSONObject().apply {
        put("kind", "span")
        put("name", this@toJson.name)
        put("traceId", spanContext.traceId)
        put("spanId", spanContext.spanId)
        if (parent.isValid) {
            put("parentSpanId", parent.spanId)
        }
        put("spanKind", spanKind.name)
        put("status", status.statusCode.name)
        put("startNanos", startTimestamp)
        endTimestamp?.let { put("endNanos", it) }
        put("attrs", attributes.toJson())
        if (events.isNotEmpty()) {
            put(
                "events",
                JSONArray(
                    events.map { event ->
                        JSONObject().apply {
                            put("name", event.name)
                            put("tsNanos", event.timestamp)
                            put("attrs", event.attributes.toJson())
                        }
                    },
                ),
            )
        }
        if (links.isNotEmpty()) {
            put(
                "links",
                JSONArray(
                    links.map { link ->
                        JSONObject().apply {
                            put("traceId", link.spanContext.traceId)
                            put("spanId", link.spanContext.spanId)
                            put("attrs", link.attributes.toJson())
                        }
                    },
                ),
            )
        }
    }

    private fun ReadWriteLogRecord.toJson(): JSONObject = JSONObject().apply {
        put("kind", "log")
        put("body", (body as? String) ?: body?.toString())
        severityNumber?.let { put("severity", it.name) }
        severityText?.takeIf { it.isNotEmpty() }?.let { put("severityText", it) }
        timestamp?.let { put("tsNanos", it) }
        observedTimestamp?.let { put("observedTsNanos", it) }
        if (spanContext.isValid) {
            put("traceId", spanContext.traceId)
            put("spanId", spanContext.spanId)
        }
        put("attrs", attributes.toJson())
    }

    private fun Map<String, Any>.toJson(): JSONObject {
        val json = JSONObject()
        forEach { (key, value) ->
            if (value is List<*>) {
                json.put(key, JSONArray(value))
            } else {
                json.put(key, value)
            }
        }
        return json
    }

    private fun emitResourceOnce(resource: Resource) {
        if (resourceEmitted.compareAndSet(false, true)) {
            emit(
                JSONObject().apply {
                    put("kind", "resource")
                    put("attrs", resource.attributes.toJson())
                },
            )
        }
    }

    private fun emit(json: JSONObject) {
        val payload = json.toString()
        val chunks = payload.chunkedByUtf8Bytes(MAX_CHUNK_BYTES)
        val seq = nextSeq.incrementAndGet()
        chunks.forEachIndexed { index, chunk ->
            Log.i(TAG, "$MARKER $seq ${index + 1}/${chunks.size} $chunk")
        }
    }

    /**
     * Splits on character boundaries (never inside a surrogate pair) so that each chunk's UTF-8
     * encoding fits within [maxBytes], keeping every logcat line under the logger payload limit
     * even for non-ASCII attribute values.
     */
    private fun String.chunkedByUtf8Bytes(maxBytes: Int): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        var bytes = 0
        var index = 0
        while (index < length) {
            val c = this[index]
            val charBytes = when {
                c.code < 0x80 -> 1
                c.code < 0x800 -> 2
                Character.isHighSurrogate(c) -> 4
                Character.isLowSurrogate(c) -> 0
                else -> 3
            }
            if (bytes + charBytes > maxBytes && bytes > 0 && !Character.isLowSurrogate(c)) {
                chunks.add(substring(start, index))
                start = index
                bytes = 0
            }
            bytes += charBytes
            index++
        }
        chunks.add(substring(start))
        return chunks
    }

    /**
     * The startup verification targets, as their on-the-wire OTel span names: these spans are all
     * recorded with `internal = true`, so the SDK prepends `emb-` to the source name
     * (OtelSpanStartArgs.initialSpanName via String.toEmbraceObjectName).
     */
    private val STARTUP_SPAN_NAMES = setOf(
        // "sdk-init" in StartupServiceImpl.recordSdkInitSpan (private span)
        "emb-sdk-init",
        // the rest come from AppStartupTraceEmitter's companion constants
        "emb-app-startup-cold",
        "emb-app-startup-warm",
        "emb-process-init",
        "emb-embrace-init",
        "emb-activity-init-delay",
        "emb-activity-init",
        "emb-activity-render",
        "emb-activity-first-draw",
        "emb-activity-load",
        "emb-app-ready",
    )

    private const val SETTING_KEY = "embrace_verify_telemetry"
    private const val TAG = "EmbVerify"
    private const val MARKER = "EMBV1"
    private const val MAX_CHUNK_BYTES = 3600
    private const val STARTUP_FLUSH_DELAY_MS = 10_000L

    private val nextSeq = AtomicInteger(0)
    private val resourceEmitted = AtomicBoolean(false)
    private val startupFlushDone = AtomicBoolean(false)
    private val pendingStartupSpans = ConcurrentLinkedQueue<SpanData>()
}
