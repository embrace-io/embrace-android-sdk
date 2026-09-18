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
import kotlin.math.max

/**
 * Turns a session part persisted by a process that died into the envelope that should be delivered for it.
 */
class SessionPartResurrector(
    private val cachedLogEnvelopeStore: CachedLogEnvelopeStore,
) {

    /**
     * Returns the envelope [deadPart] should be delivered as, or null if it does not contain exactly one
     * session part span. [processIdentifier] is the process that persisted it.
     */
    fun resurrect(
        deadPart: Envelope<SessionPartPayload>,
        processIdentifier: String,
        nativeCrashService: NativeCrashService?,
        nativeCrashProvider: (String) -> NativeCrashData?,
        onNativeCrashProcessed: (NativeCrashData) -> Unit,
        userSessionTerminationReason: String?,
        isBackgroundOnly: Boolean,
    ): Envelope<SessionPartPayload>? {
        val deadSessionPartSpan = deadPart.getSessionPartSpan()
        val sessionPartId = deadSessionPartSpan?.resolveSessionPartIdForCrashMatch()

        val nativeCrash = if (nativeCrashService != null && sessionPartId != null) {
            nativeCrashProvider(sessionPartId)?.apply {
                val nativeCrashEnvelopeMetadata = createNativeCrashEnvelopeMetadata(
                    sessionPartId = sessionPartId,
                    processIdentifier = processIdentifier,
                    userSessionId = userSessionId,
                )

                cachedLogEnvelopeStore.create(
                    storedTelemetryMetadata = nativeCrashEnvelopeMetadata,
                    resource = deadPart.resource ?: EnvelopeResource(),
                    metadata = deadPart.metadata ?: EnvelopeMetadata(),
                )

                nativeCrashService.sendNativeCrash(
                    nativeCrash = this,
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

                onNativeCrashProcessed(this)
            }
        } else {
            null
        }

        return deadPart.resurrectSession(
            nativeCrashData = nativeCrash,
            userSessionTerminationReason = userSessionTerminationReason,
            isBackgroundOnly = isBackgroundOnly,
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
    ): Envelope<SessionPartPayload>? {
        val completedSpanIds = data.spans?.map { it.spanId }?.toSet() ?: emptySet()
        val failedSpans = data.spanSnapshots
            ?.filterNot { completedSpanIds.contains(it.spanId) }
            ?.map { it.toFailedSpan(endTimeMs = getFailedSpanEndTimeMs(this)) }
            ?: emptyList()
        val completedSpans = (data.spans ?: emptyList()) + failedSpans
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

    /**
     * To approximate the time of any snapshot to be converted into a failed span, we look to the session part span of the payload and take
     * either the end time or the last heartbeat time, whichever exists and is later. If the session part span itself is a snapshot, it will
     * not have an end time, in which case it will fall back to the last heartbeat time. If either exists, it means we can't find a better
     * time, so we just leave it at 0.
     */
    private fun getFailedSpanEndTimeMs(envelope: Envelope<SessionPartPayload>): Long {
        val sessionPartSpan = envelope.getSessionPartSpan() ?: return 0L
        val endTimeMs = sessionPartSpan.endTimeNanos ?: 0L
        val lastHeartbeatTimeMs =
            sessionPartSpan.attributes?.findAttributeValue(EmbSessionAttributes.EMB_HEARTBEAT_TIME_UNIX_NANO)?.toLongOrNull() ?: 0L
        return max(endTimeMs, lastHeartbeatTimeMs).nanosToMillis()
    }
}
