@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore.Companion.createNativeCrashEnvelopeMetadata
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValues
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.spans.toFailedSpan
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.getSessionPartSpan
import io.embrace.android.embracesdk.internal.session.getUserSessionProperties
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv

/**
 * Turns a session part persisted by a process that died into the envelope that should be delivered for it.
 *
 * Used by [PayloadResurrectionServiceImpl] for session payloads cached in single-file mode, and by
 * [MultiFileDeadPartResurrector] for dead parts the multi-file session part reader finds on disk.
 */
class SessionPartResurrector(
    private val cachedLogEnvelopeStore: CachedLogEnvelopeStore,
) {

    /**
     * Returns the envelope [deadPart] should be delivered as, or null if it does not contain exactly one
     * session part span. [processIdentifier] is the process that persisted it, and [lastWrittenMs] is the
     * last time its files were written, if they record it.
     *
     * The native crash recorded for [deadPart], if any, is attached to the envelope, then sent and reported to
     * [onNativeCrashProcessed] by the work handed to [sendNativeCrash]. That work runs at once unless the caller
     * holds it back, such as until the envelope has been stored.
     */
    fun resurrect(
        deadPart: Envelope<SessionPartPayload>,
        processIdentifier: String,
        nativeCrashService: NativeCrashService?,
        nativeCrashProvider: (String) -> NativeCrashData?,
        onNativeCrashProcessed: (NativeCrashData) -> Unit,
        userSessionTerminationReason: String?,
        isBackgroundOnly: Boolean,
        lastWrittenMs: Long? = null,
        sendNativeCrash: (send: () -> Unit) -> Unit = { send -> send() },
    ): Envelope<SessionPartPayload>? {
        val deadSessionPartSpan = deadPart.getSessionPartSpan()
        val sessionPartId = deadSessionPartSpan?.resolveSessionPartIdForCrashMatch()

        val nativeCrash = if (nativeCrashService != null && sessionPartId != null) {
            nativeCrashProvider(sessionPartId)?.also { crash ->
                sendNativeCrash {
                    val nativeCrashEnvelopeMetadata = createNativeCrashEnvelopeMetadata(
                        sessionPartId = sessionPartId,
                        processIdentifier = processIdentifier,
                        userSessionId = crash.userSessionId,
                    )

                    cachedLogEnvelopeStore.create(
                        storedTelemetryMetadata = nativeCrashEnvelopeMetadata,
                        resource = deadPart.resource ?: EnvelopeResource(),
                        metadata = deadPart.metadata ?: EnvelopeMetadata(),
                    )

                    nativeCrashService.sendNativeCrash(
                        nativeCrash = crash,
                        userSessionProperties = deadPart.getUserSessionProperties(),
                        metadata = buildMap {
                            put(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER, processIdentifier)
                            deadSessionPartSpan.attributes?.findAttributeValues(
                                setOf(
                                    EmbSessionAttributes.EMB_STATE,
                                    EmbCommonAttributes.EMB_EXPERIMENTS,
                                ),
                            )?.let(::putAll)
                        },
                    )

                    onNativeCrashProcessed(crash)
                }
            }
        } else {
            null
        }

        return deadPart.resurrectSession(
            nativeCrashData = nativeCrash,
            userSessionTerminationReason = userSessionTerminationReason,
            isBackgroundOnly = isBackgroundOnly,
            lastWrittenMs = lastWrittenMs,
        )
    }

    /**
     * Return copy of envelope with a modified set of spans to reflect their resurrected states, or null if the
     * payload does not contain exactly one session part span.
     */
    private fun Envelope<SessionPartPayload>.resurrectSession(
        nativeCrashData: NativeCrashData?,
        userSessionTerminationReason: String?,
        isBackgroundOnly: Boolean,
        lastWrittenMs: Long?,
    ): Envelope<SessionPartPayload>? {
        val completedSpanIds = data.spans?.map { it.spanId }?.toSet() ?: emptySet()
        val snapshots = data.spanSnapshots
            ?.filterNot { completedSpanIds.contains(it.spanId) }

        val persistedSpans = data.spans.orEmpty()

        // In multi-file mode, lastWrittenMs is the last time the part's files were written, which the process had
        // to be alive to do. It is the end of the part even when a span in the payload records a later time, as
        // span times can be set by the app. A span that started after lastWrittenMs will have its end time set to
        // its start time rather than the lastWrittenMs because a span can't end before its starts.
        // For files where we don't have lastWrittenMs or if we are in single-file mode, estimate the end from the
        // payload's span times.
        val estimatedEndTimeMs = lastWrittenMs ?: estimateEndTimeMs(persistedSpans, snapshots.orEmpty())
        val failedSpans = snapshots
            ?.map { it.toFailedSpan(endTimeMs = maxOf(estimatedEndTimeMs, it.startTimeMs())) }
            ?: emptyList()
        val completedSpans = persistedSpans + failedSpans
        val sessionPartSpan = completedSpans.singleOrNull { it.hasEmbraceAttribute(EmbType.Ux.Session) } ?: return null

        val attributesToAttach = buildList {
            if (isBackgroundOnly) {
                addAll(sessionPartSpan.backgroundOnlyAttributes())
            }
            if (userSessionTerminationReason != null) {
                addAll(sessionPartSpan.finalSessionPartAttributes(userSessionTerminationReason))
            }
            if (nativeCrashData != null) {
                addAll(sessionPartSpan.crashAttributes(nativeCrashData))
            }
        }

        val spans = if (attributesToAttach.isEmpty()) {
            completedSpans
        } else {
            val updatedSessionPartSpan = sessionPartSpan.copy(attributes = (sessionPartSpan.attributes ?: emptyList()) + attributesToAttach)
            completedSpans.minus(sessionPartSpan).plus(updatedSessionPartSpan)
        }

        return copy(
            data = data.copy(
                spans = spans,
                spanSnapshots = emptyList(),
            ),
        )
    }

    /**
     * Estimates when the process that left this part died, in milliseconds, as the latest of any completed
     * span's end and any snapshot's start. Completed spans' end times alone aren't enough, as spans carried
     * over from a previous session part can end before a snapshot in this part started. 0 if nothing is
     * known.
     */
    private fun estimateEndTimeMs(completedSpans: List<Span>, snapshots: List<Span>): Long {
        var latestNanos = 0L
        for (i in completedSpans.indices) {
            latestNanos = maxOf(latestNanos, completedSpans[i].endTimeNanos ?: 0L)
        }
        for (i in snapshots.indices) {
            latestNanos = maxOf(latestNanos, snapshots[i].startTimeNanos ?: 0L)
        }
        return latestNanos.nanosToMillis()
    }

    private fun Span.startTimeMs(): Long = (startTimeNanos ?: 0L).nanosToMillis()

    /**
     * Attribute marking this session part as belonging to a background-only user session.
     */
    private fun Span.backgroundOnlyAttributes(): List<Attribute> =
        if (attributes?.hasEmbraceAttributeKey(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART) == false) {
            listOf(Attribute(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART, "1"))
        } else {
            emptyList()
        }

    /**
     * Attributes marking this session part as the final one of a terminated user session, or empty if it is already marked final.
     */
    private fun Span.finalSessionPartAttributes(terminationReason: String): List<Attribute> =
        if (attributes?.hasEmbraceAttributeKey(EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART) == false) {
            listOf(
                Attribute(EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART, "1"),
                Attribute(EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON, terminationReason),
            )
        } else {
            emptyList()
        }

    /**
     * Attributes attaching the native crash to this session part span if its session part id matches, or empty otherwise.
     */
    private fun Span.crashAttributes(nativeCrashData: NativeCrashData): List<Attribute> {
        val sessionPartId = resolveSessionPartIdForCrashMatch()
        return if (sessionPartId != null && sessionPartId == nativeCrashData.sessionPartId) {
            listOf(Attribute(EmbSessionAttributes.EMB_CRASH_ID, nativeCrashData.nativeCrashId))
        } else {
            emptyList()
        }
    }

    /**
     * Resolves the session part id used to match a native crash to this session part.
     *
     * A session part span always has [EmbSessionAttributes.EMB_SESSION_PART_ID], so we take whatever value it defines. If it does not
     * exist, this session part span is not valid, so we return null.
     */
    private fun Span.resolveSessionPartIdForCrashMatch(): String? =
        attributes?.findAttributeValue(EmbSessionAttributes.EMB_SESSION_PART_ID)
}
