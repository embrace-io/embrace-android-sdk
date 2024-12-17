package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore.Companion.createNativeCrashEnvelopeMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.opentelemetry.embCrashId
import io.embrace.android.embracesdk.internal.opentelemetry.embHeartbeatTimeUnixNano
import io.embrace.android.embracesdk.internal.opentelemetry.embProcessIdentifier
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.getSessionId
import io.embrace.android.embracesdk.internal.payload.getSessionProperties
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.payload.toFailedSpan
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.internal.utils.Provider
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.max

internal class PayloadResurrectionServiceImpl(
    private val intakeService: IntakeService,
    private val cacheStorageService: PayloadStorageService,
    private val cachedLogEnvelopeStore: CachedLogEnvelopeStore,
    private val logger: EmbLogger,
    private val serializer: PlatformSerializer,
) : PayloadResurrectionService {

    override fun resurrectOldPayloads(nativeCrashServiceProvider: Provider<NativeCrashService?>) {
        val nativeCrashService = nativeCrashServiceProvider()
        val undeliveredPayloads = cacheStorageService.getUndeliveredPayloads()
        val payloadsToResurrect = undeliveredPayloads.filterNot { it.isCrashEnvelope() }
        val nativeCrashes = nativeCrashService?.getNativeCrashes()?.associateBy { it.sessionId } ?: emptyMap()
        val processedCrashes = mutableSetOf<NativeCrashData>()

        payloadsToResurrect.forEach { payload ->
            val result = runCatching {
                payload.processUndeliveredPayload(
                    nativeCrashService = nativeCrashService,
                    nativeCrashProvider = nativeCrashes::get,
                    postNativeCrashProcessingCallback = processedCrashes::add,
                )
            }

            if (result.isSuccess) {
                cacheStorageService.delete(payload)
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

        if (nativeCrashService != null) {
            // We assume that there can ever only be one cached crash envelope and one sessionless native crash
            // Internal errors will be logged if that assumption is not true, as we currently don't store enough
            // metadata in the native crash to determine which app instance it came from if it isn't associated with
            // a session.
            //
            // This assumption would be incorrect if a native crash happens during startup, before a session is created,
            // and before the payload resurrection phase of the SDK startup has completed. This seems pretty rare.
            //
            // Solving this requires the persistence of the processIdentifier, and we will only do this if this
            // proves to be a problem in production.

            val sessionlessNativeCrashes = nativeCrashes.values.filterNot { processedCrashes.contains(it) }
            if (sessionlessNativeCrashes.isNotEmpty()) {
                val cachedCrashEnvelopeMetadata = undeliveredPayloads.firstOrNull { it.isCrashEnvelope() }
                val cachedCrashEnvelope = if (cachedCrashEnvelopeMetadata != null) {
                    runCatching {
                        serializer.fromJson<Envelope<LogPayload>>(
                            inputStream = GZIPInputStream(
                                cacheStorageService.loadPayloadAsStream(cachedCrashEnvelopeMetadata)
                            ),
                            type = SupportedEnvelopeType.CRASH.serializedType
                        ).also {
                            cacheStorageService.delete(cachedCrashEnvelopeMetadata)
                        }
                    }.getOrNull()
                } else {
                    null
                }
                val resource = cachedCrashEnvelope?.resource
                val metadata = cachedCrashEnvelope?.metadata
                sessionlessNativeCrashes.forEach { nativeCrash ->
                    if (resource != null && metadata != null) {
                        cachedLogEnvelopeStore.create(
                            storedTelemetryMetadata = createNativeCrashEnvelopeMetadata(
                                sessionId = nativeCrash.sessionId
                            ),
                            resource = resource,
                            metadata = metadata
                        )
                    } else {
                        logger.trackInternalError(
                            type = InternalErrorType.NATIVE_CRASH_RESURRECTION_ERROR,
                            throwable = IllegalStateException("Cached native crash envelope data not found")
                        )
                    }
                    nativeCrashService.sendNativeCrash(
                        nativeCrash = nativeCrash,
                        sessionProperties = emptyMap(),
                        metadata = mapOf(
                            embState.attributeKey to ApplicationState.BACKGROUND.name.lowercase(Locale.ENGLISH)
                        ),
                    )
                }
                if (sessionlessNativeCrashes.size > 1) {
                    logger.trackInternalError(
                        type = InternalErrorType.NATIVE_CRASH_RESURRECTION_ERROR,
                        throwable = IllegalStateException("Multiple sessionless native crashes found.")
                    )
                }
            }
            nativeCrashService.deleteAllNativeCrashes()
        }

        undeliveredPayloads.filter { it.isCrashEnvelope() }.forEach { crashEnvelopeMetadata ->
            cacheStorageService.delete(crashEnvelopeMetadata)
        }
        cachedLogEnvelopeStore.clear()
    }

    private fun StoredTelemetryMetadata.isCrashEnvelope() = envelopeType == SupportedEnvelopeType.CRASH

    private fun StoredTelemetryMetadata.processUndeliveredPayload(
        nativeCrashService: NativeCrashService?,
        nativeCrashProvider: (String) -> NativeCrashData?,
        postNativeCrashProcessingCallback: (NativeCrashData) -> Unit,
    ) {
        val resurrectedPayload = when (envelopeType) {
            SupportedEnvelopeType.SESSION -> {
                val deadSession = serializer.fromJson<Envelope<SessionPayload>>(
                    inputStream = GZIPInputStream(cacheStorageService.loadPayloadAsStream(this)),
                    type = envelopeType.serializedType
                )

                val sessionId = deadSession.getSessionId()
                val appState = deadSession.getSessionSpan()?.attributes?.findAttributeValue(embState.name)
                val nativeCrash = if (nativeCrashService != null && sessionId != null) {
                    nativeCrashProvider(sessionId)?.apply {
                        val nativeCrashEnvelopeMetadata = createNativeCrashEnvelopeMetadata(
                            sessionId = sessionId,
                            processIdentifier = processId
                        )

                        cachedLogEnvelopeStore.create(
                            storedTelemetryMetadata = nativeCrashEnvelopeMetadata,
                            resource = deadSession.resource ?: EnvelopeResource(),
                            metadata = deadSession.metadata ?: EnvelopeMetadata()
                        )

                        nativeCrashService.sendNativeCrash(
                            nativeCrash = this,
                            sessionProperties = deadSession.getSessionProperties(),
                            metadata = if (appState != null) {
                                mapOf(
                                    embState.attributeKey to appState,
                                    embProcessIdentifier.attributeKey to processId
                                )
                            } else {
                                emptyMap()
                            },
                        )

                        postNativeCrashProcessingCallback(this)
                    }
                } else {
                    null
                }

                deadSession.resurrectSession(nativeCrash)
                    ?: throw IllegalArgumentException(
                        "Session resurrection failed. Payload does not contain exactly one session span."
                    )
            }

            else -> null
        }

        if (resurrectedPayload != null) {
            intakeService.take(
                intake = resurrectedPayload,
                metadata = copy(complete = true)
            )
        }
    }

    /**
     * Return copy of envelope with a modified set of spans to reflect their resurrected states, or null if the
     * payload does not contain exactly one session span.
     */
    private fun Envelope<SessionPayload>.resurrectSession(
        nativeCrashData: NativeCrashData?,
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
