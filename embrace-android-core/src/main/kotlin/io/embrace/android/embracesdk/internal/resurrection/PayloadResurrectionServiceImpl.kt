package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.opentelemetry.embCrashId
import io.embrace.android.embracesdk.internal.opentelemetry.embHeartbeatTimeUnixNano
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.getSessionId
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.payload.toFailedSpan
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.internal.utils.Provider
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import java.util.zip.GZIPInputStream
import kotlin.math.max

internal class PayloadResurrectionServiceImpl(
    private val intakeService: IntakeService,
    private val payloadStorageService: PayloadStorageService,
    private val nativeCrashServiceProvider: Provider<NativeCrashService?>,
    private val logger: EmbLogger,
    private val serializer: PlatformSerializer,
) : PayloadResurrectionService {

    override fun resurrectOldPayloads() {
        val nativeCrashService = nativeCrashServiceProvider()
        val nativeCrashes = nativeCrashService?.getNativeCrashes()?.associateBy { it.sessionId } ?: emptyMap()
        nativeCrashes.values.forEach { nativeCrash ->
            nativeCrashService?.sendNativeCrash(nativeCrash)
        }
        payloadStorageService
            .getUndeliveredPayloads()
            .forEach { deadSessionMetadata ->
                deadSessionMetadata.sendResurrectedPayload(nativeCrashes::get)
            }
        // TODO: delete each native crash after its associated session is resurrected and sent
        nativeCrashService?.deleteAllNativeCrashes()
    }

    /**
     * Load and modify the given incomplete payload envelope and send the result to the [IntakeService] for delivery.
     * Resurrected payloads sent to the [IntakeService] will be deleted.
     */
    private fun StoredTelemetryMetadata.sendResurrectedPayload(nativeCrashProvider: (String) -> NativeCrashData?) {
        val result = runCatching {
            val deadPayload = when (envelopeType) {
                SupportedEnvelopeType.SESSION -> {
                    val deadSession = serializer.fromJson<Envelope<SessionPayload>>(
                        inputStream = GZIPInputStream(payloadStorageService.loadPayloadAsStream(this)),
                        type = envelopeType.serializedType
                    )

                    val nativeCrash = deadSession.getSessionId()?.run {
                        nativeCrashProvider(this)
                    }

                    deadSession.resurrectSession(nativeCrash)
                        ?: throw IllegalArgumentException(
                            "Session resurrection failed. Payload does not contain exactly one session span."
                        )
                }

                else -> null
            }

            if (deadPayload != null) {
                intakeService.take(
                    intake = deadPayload,
                    metadata = this
                )
            }
        }

        if (result.isSuccess) {
            payloadStorageService.delete(this)
        } else {
            val exception = IllegalStateException(
                "Resurrecting and sending incomplete payloads from previous app launches failed.",
                result.exceptionOrNull()
            )

            logger.trackInternalError(
                type = InternalErrorType.PAYLOAD_RESURRECTION_FAIL,
                throwable = exception
            )
        }
    }

    /**
     * Return copy of envelope with a modified set of spans to reflect their resurrected states, or null if payload does not contain
     * exactly one session span.
     */
    private fun Envelope<SessionPayload>.resurrectSession(
        nativeCrashData: NativeCrashData?
    ): Envelope<SessionPayload>? {
        val completedSpanIds = data.spans?.map { it.spanId }?.toSet() ?: emptySet()
        val failedSpans = data.spanSnapshots
            ?.filterNot { completedSpanIds.contains(it.spanId) }
            ?.map { it.toFailedSpan(endTimeMs = getFailedSpanEndTimeMs(this)) }
            ?: emptyList()
        val completedSpans = (data.spans ?: emptyList()) + failedSpans
        val sessionSpan = completedSpans.singleOrNull { it.hasFixedAttribute(EmbType.Ux.Session) } ?: return null
        val spans = if (nativeCrashData != null) {
            completedSpans.minus(sessionSpan).plus(
                sessionSpan.attachCrashToSession(
                    nativeCrashData = nativeCrashData
                )
            )
        } else {
            completedSpans
        }

        return copy(
            data = data.copy(
                spans = spans,
                spanSnapshots = emptyList(),
            )
        )
    }

    /**
     * Attach crash data to the existing session span in the payload if it exists
     */
    private fun Span.attachCrashToSession(nativeCrashData: NativeCrashData): Span {
        return if (attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key) == nativeCrashData.sessionId) {
            copy(
                attributes = attributes?.plus(
                    Attribute(
                        embCrashId.name,
                        nativeCrashData.nativeCrashId
                    )
                )
            )
        } else {
            this
        }
    }

    /**
     * To approximate the time of any snapshot to be converted into a failed span, we look to the session span of the payload and take
     * either the end time or the last heartbeat time, whichever exists and is later. If the session span itself is a snapshot, it will
     * not have an end time, in which case it will fall back to the last heartbeat time. If either exists, it means we can't find a better
     * time, so we just leave it at 0.
     */
    private fun getFailedSpanEndTimeMs(envelope: Envelope<SessionPayload>): Long {
        val sessionSpan = envelope.getSessionSpan() ?: return 0L
        val endTimeMs = sessionSpan.endTimeNanos ?: 0L
        val lastHeartbeatTimeMs =
            sessionSpan.attributes?.findAttributeValue(embHeartbeatTimeUnixNano.attributeKey.key)?.toLongOrNull() ?: 0L
        return max(endTimeMs, lastHeartbeatTimeMs).nanosToMillis()
    }
}
