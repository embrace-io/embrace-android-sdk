package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore.Companion.createNativeCrashEnvelopeMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.spans.toFailedSpan
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.serialization.fromJson
import io.embrace.android.embracesdk.internal.session.UserSessionRestoreDecision
import io.embrace.android.embracesdk.internal.session.getSessionId
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.internal.session.getUserSessionProperties
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.SessionAttributes
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException
import kotlin.math.max

internal class PayloadResurrectionServiceImpl(
    private val intakeService: IntakeService,
    private val payloadStorageService: PayloadStorageService,
    private val cacheStorageService: PayloadStorageService,
    private val cachedLogEnvelopeStore: CachedLogEnvelopeStore,
    private val logger: InternalLogger,
    private val serializer: PlatformSerializer,
) : PayloadResurrectionService {

    private val completionListeners = CopyOnWriteArrayList<() -> Unit>()

    override fun addResurrectionCompleteListener(listener: () -> Unit) {
        completionListeners.add(listener)
    }

    override fun resurrectOldPayloads(
        nativeCrashServiceProvider: Provider<NativeCrashService?>,
        userSessionRestoreDecisionProvider: Provider<UserSessionRestoreDecision?>,
    ) {
        runCatching {
            processTombstones(nativeCrashServiceProvider, userSessionRestoreDecisionProvider())
        }.onFailure {
            logger.trackInternalError(InternalErrorType.PayloadResurrectionFail, it)
        }
        completionListeners.forEach { listener ->
            runCatching {
                listener()
            }.onFailure {
                logger.trackInternalError(InternalErrorType.PayloadResurrectionFail, it)
            }
        }
    }

    private fun processTombstones(
        nativeCrashServiceProvider: Provider<NativeCrashService?>,
        restoreDecision: UserSessionRestoreDecision?,
    ) {
        val nativeCrashService = nativeCrashServiceProvider()
        val undeliveredPayloads = cacheStorageService.getUndeliveredPayloads()
        val nonCrashPayloads = undeliveredPayloads.filterNot { it.isCrashEnvelope() }
        val (payloadsToResurrect, redundantPayloads) = dedupeSessionPayloads(nonCrashPayloads)
        val nativeCrashes = nativeCrashService?.getNativeCrashes()?.associateBy { it.sessionPartId } ?: emptyMap()
        val processedCrashes = mutableSetOf<NativeCrashData>()

        // delete duplicate cached payloads as the surviving copy has already been stored in payloadStorageService
        redundantPayloads.forEach { cacheStorageService.delete(it) }

        // Only a terminated session has a final part to stamp
        val terminatedUserSession = restoreDecision as? UserSessionRestoreDecision.Terminated
        val lastPartForTerminatedUserSession = if (terminatedUserSession != null) {
            lastSessionPartForUserSession(payloadsToResurrect, terminatedUserSession.userSessionId)
        } else {
            null
        }
        payloadsToResurrect.forEach { payload ->
            val userSessionTerminationReason = if (lastPartForTerminatedUserSession != null) {
                terminatedUserSession?.reason?.takeIf { payload == lastPartForTerminatedUserSession }
            } else {
                null
            }

            runCatching {
                payload.processUndeliveredPayload(
                    nativeCrashService = nativeCrashService,
                    nativeCrashProvider = nativeCrashes::get,
                    postNativeCrashProcessingCallback = processedCrashes::add,
                    userSessionTerminationReason = userSessionTerminationReason,
                    isBackgroundOnly = restoreDecision?.takeIf { it.userSessionId == payload.userSessionId }?.backgroundOnly == true,
                )
            }.onFailure {
                logger.trackInternalError(
                    type = InternalErrorType.PayloadResurrectionPayloadFail,
                    throwable = it
                )
            }

            // Delete every processed. If there's a failure resurrecting a particular payload, assume it is not recoverable
            // and log the instance so we know it happened.
            cacheStorageService.delete(payload)
        }

        if (nativeCrashService != null) {
            processNativeCrashes(nativeCrashes, processedCrashes, undeliveredPayloads, nativeCrashService)
        }

        undeliveredPayloads.filter { it.isCrashEnvelope() }.forEach { crashEnvelopeMetadata ->
            cacheStorageService.delete(crashEnvelopeMetadata)
        }
        cachedLogEnvelopeStore.clear()
    }

    private fun processNativeCrashes(
        nativeCrashes: Map<String, NativeCrashData>,
        processedCrashes: MutableSet<NativeCrashData>,
        undeliveredPayloads: List<StoredTelemetryMetadata>,
        nativeCrashService: NativeCrashService,
    ) {
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
            val cachedCrashEnvelope = cachedCrashEnvelopeMetadata
                ?.loadDecompressedPayload()
                ?.let { payloadStream ->
                    runCatching {
                        serializer.fromJson<Envelope<LogPayload>>(payloadStream)
                    }.getOrNull()
                }
                ?.also { runCatching { cacheStorageService.delete(cachedCrashEnvelopeMetadata) } }
            val resource = cachedCrashEnvelope?.resource
            val metadata = cachedCrashEnvelope?.metadata
            sessionlessNativeCrashes.forEach { nativeCrash ->
                if (resource != null && metadata != null) {
                    cachedLogEnvelopeStore.create(
                        storedTelemetryMetadata = createNativeCrashEnvelopeMetadata(
                            sessionPartId = nativeCrash.sessionPartId,
                            userSessionId = nativeCrash.userSessionId,
                        ),
                        resource = resource,
                        metadata = metadata
                    )
                } else {
                    logger.trackInternalError(
                        type = InternalErrorType.NativeCrashResurrectionError,
                        throwable = IllegalStateException("Cached native crash envelope data not found")
                    )
                }
                nativeCrashService.sendNativeCrash(
                    nativeCrash = nativeCrash,
                    userSessionProperties = emptyMap(),
                    metadata = mapOf(
                        EmbSessionAttributes.EMB_STATE to AppState.BACKGROUND.description,
                    ),
                )
            }
            if (sessionlessNativeCrashes.size > 1) {
                logger.trackInternalError(
                    type = InternalErrorType.NativeCrashResurrectionError,
                    throwable = IllegalStateException("Multiple sessionless native crashes found.")
                )
            }
        }
        nativeCrashService.deleteAllNativeCrashes()
    }

    private fun StoredTelemetryMetadata.isCrashEnvelope() = envelopeType == SupportedEnvelopeType.CRASH

    /**
     * The last session part of the user session with ID [terminatedUserSessionId] among the resurrected payloads, or null if there is
     * no session part from this terminated user session.
     */
    private fun lastSessionPartForUserSession(
        payloads: List<StoredTelemetryMetadata>,
        terminatedUserSessionId: String,
    ): StoredTelemetryMetadata? = terminatedUserSessionId?.let {
        payloads
            .filter { payload -> payload.envelopeType == SupportedEnvelopeType.SESSION && payload.userSessionId == terminatedUserSessionId }
            .maxByOrNull { payload -> payload.timestamp }
    }

    /**
     * Splits payloads into two collections: one that should be resurrected, and another that should be
     * discarded.
     *
     * Cached session payloads can be redundant either when another cached payload has the same session
     * ID, or when the session ID is matched by a complete payload in [payloadStorageService].
     *
     * Non-SESSION payloads and SESSION payloads with no encoded session IDs (legacy v1 filenames)
     * bypass the dedupe checks.
     */
    private fun dedupeSessionPayloads(
        candidates: List<StoredTelemetryMetadata>,
    ): Pair<List<StoredTelemetryMetadata>, List<StoredTelemetryMetadata>> {
        val alreadyDeliverable: Set<SessionKey> = payloadStorageService.getPayloadsByPriority()
            .mapNotNullTo(mutableSetOf()) { it.sessionKey() }

        // For each session key in the cache, find the latest timestamp — that's the winner.
        val latestTimestampPerKey = mutableMapOf<SessionKey, Long>()
        candidates.forEach { meta ->
            val key = meta.sessionKey() ?: return@forEach
            val current = latestTimestampPerKey[key]
            if (current == null || meta.timestamp > current) {
                latestTimestampPerKey[key] = meta.timestamp
            }
        }

        val deliverables = mutableListOf<StoredTelemetryMetadata>()
        val redundant = mutableListOf<StoredTelemetryMetadata>()
        val emittedKeys = mutableSetOf<SessionKey>()

        candidates.forEach { meta ->
            val key = meta.sessionKey()
            when {
                key == null -> deliverables += meta
                key in alreadyDeliverable -> redundant += meta
                key !in emittedKeys && meta.timestamp == latestTimestampPerKey[key] -> {
                    deliverables += meta
                    emittedKeys += key
                }

                else -> redundant += meta
            }
        }
        return deliverables to redundant
    }

    private fun StoredTelemetryMetadata.sessionKey(): SessionKey? {
        return if (envelopeType == SupportedEnvelopeType.SESSION &&
            (userSessionId.isNotEmpty() || sessionPartId.isNotEmpty())
        ) {
            SessionKey(userSessionId, sessionPartId)
        } else {
            null
        }
    }

    private data class SessionKey(val userSessionId: String, val sessionPartId: String)

    private fun StoredTelemetryMetadata.processUndeliveredPayload(
        nativeCrashService: NativeCrashService?,
        nativeCrashProvider: (String) -> NativeCrashData?,
        postNativeCrashProcessingCallback: (NativeCrashData) -> Unit,
        userSessionTerminationReason: String?,
        isBackgroundOnly: Boolean,
    ) {
        val resurrectedPayload = when (envelopeType) {
            SupportedEnvelopeType.SESSION -> {
                loadDecompressedPayload()?.let { payloadStream ->
                    processUndeliveredPayloadImpl(
                        payloadStream,
                        nativeCrashService,
                        nativeCrashProvider,
                        postNativeCrashProcessingCallback,
                        userSessionTerminationReason,
                        isBackgroundOnly,
                    )
                }
            }

            else -> null
        }

        // Synchronously provide the payload to the IntakeService, blocking on the returned Future
        if (resurrectedPayload != null) {
            val task = intakeService.take(
                intake = resurrectedPayload,
                metadata = copy(complete = true),
                staleEntry = this,
            )
            try {
                task.get(5, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                logger.trackInternalError(InternalErrorType.IntakeFail, e)
            }
        }
    }

    private fun StoredTelemetryMetadata.processUndeliveredPayloadImpl(
        payloadStream: InputStream,
        nativeCrashService: NativeCrashService?,
        nativeCrashProvider: (String) -> NativeCrashData?,
        postNativeCrashProcessingCallback: (NativeCrashData) -> Unit,
        userSessionTerminationReason: String?,
        isBackgroundOnly: Boolean,
    ): Envelope<SessionPartPayload> {
        val deadPart = serializer.fromJson<Envelope<SessionPartPayload>>(payloadStream)

        val sessionId = deadPart.getSessionId()
        val appState = deadPart.getSessionSpan()?.attributes?.findAttributeValue(EmbSessionAttributes.EMB_STATE)
        val nativeCrash = if (nativeCrashService != null && sessionId != null) {
            nativeCrashProvider(sessionId)?.apply {
                val nativeCrashEnvelopeMetadata = createNativeCrashEnvelopeMetadata(
                    sessionPartId = sessionId,
                    processIdentifier = processIdentifier,
                    userSessionId = userSessionId,
                )

                cachedLogEnvelopeStore.create(
                    storedTelemetryMetadata = nativeCrashEnvelopeMetadata,
                    resource = deadPart.resource ?: EnvelopeResource(),
                    metadata = deadPart.metadata ?: EnvelopeMetadata()
                )

                nativeCrashService.sendNativeCrash(
                    nativeCrash = this,
                    userSessionProperties = deadPart.getUserSessionProperties(),
                    metadata = if (appState != null) {
                        mapOf(
                            EmbSessionAttributes.EMB_STATE to appState,
                            EmbSessionAttributes.EMB_PROCESS_IDENTIFIER to processIdentifier
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

        return deadPart.resurrectSession(
            nativeCrashData = nativeCrash,
            userSessionTerminationReason = userSessionTerminationReason,
            isBackgroundOnly = isBackgroundOnly
        ) ?: throw IllegalArgumentException(
            "Session resurrection failed. Payload does not contain exactly one session span."
        )
    }

    /**
     * Return copy of envelope with a modified set of spans to reflect their resurrected states, or null if the
     * payload does not contain exactly one session span.
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
        val sessionSpan = completedSpans.singleOrNull { it.hasEmbraceAttribute(EmbType.Ux.Session) } ?: return null

        val attributesToAttach = buildList {
            if (isBackgroundOnly) {
                addAll(sessionSpan.backgroundOnlyAttributes())
            }
            if (userSessionTerminationReason != null) {
                addAll(sessionSpan.finalSessionPartAttributes(userSessionTerminationReason))
            }
            if (nativeCrashData != null) {
                addAll(sessionSpan.crashAttributes(nativeCrashData))
            }
        }

        val spans = if (attributesToAttach.isEmpty()) {
            completedSpans
        } else {
            val updatedSessionSpan = sessionSpan.copy(attributes = (sessionSpan.attributes ?: emptyList()) + attributesToAttach)
            completedSpans.minus(sessionSpan).plus(updatedSessionSpan)
        }

        return copy(
            data = data.copy(
                spans = spans,
                spanSnapshots = emptyList(),
            )
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
     * Attributes attaching the native crash to this session span if its session id matches, or empty otherwise.
     */
    private fun Span.crashAttributes(nativeCrashData: NativeCrashData): List<Attribute> =
        if (attributes?.findAttributeValue(SessionAttributes.SESSION_ID) == nativeCrashData.sessionPartId) {
            listOf(Attribute(EmbSessionAttributes.EMB_CRASH_ID, nativeCrashData.nativeCrashId))
        } else {
            emptyList()
        }

    private fun StoredTelemetryMetadata.loadDecompressedPayload(): InputStream? =
        cacheStorageService.loadPayloadAsStream(this)?.let {
            try {
                GZIPInputStream(it)
            } catch (_: ZipException) {
                null
            }
        }

    /**
     * To approximate the time of any snapshot to be converted into a failed span, we look to the session span of the payload and take
     * either the end time or the last heartbeat time, whichever exists and is later. If the session span itself is a snapshot, it will
     * not have an end time, in which case it will fall back to the last heartbeat time. If either exists, it means we can't find a better
     * time, so we just leave it at 0.
     */
    private fun getFailedSpanEndTimeMs(envelope: Envelope<SessionPartPayload>): Long {
        val sessionSpan = envelope.getSessionSpan() ?: return 0L
        val endTimeMs = sessionSpan.endTimeNanos ?: 0L
        val lastHeartbeatTimeMs =
            sessionSpan.attributes?.findAttributeValue(EmbSessionAttributes.EMB_HEARTBEAT_TIME_UNIX_NANO)?.toLongOrNull() ?: 0L
        return max(endTimeMs, lastHeartbeatTimeMs).nanosToMillis()
    }
}
