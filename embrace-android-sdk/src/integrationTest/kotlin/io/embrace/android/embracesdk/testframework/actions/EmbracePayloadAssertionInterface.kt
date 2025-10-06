package io.embrace.android.embracesdk.testframework.actions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.internal.TypeUtils
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.otel.attrs.embCleanExit
import io.embrace.android.embracesdk.internal.otel.attrs.embCrashId
import io.embrace.android.embracesdk.internal.otel.attrs.embState
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.testframework.assertions.JsonComparator
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.testframework.server.FakeApiServer
import io.embrace.android.embracesdk.testframework.server.FormPart
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeoutException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

/**
 * Provides assertions that can be used in integration tests to validate the behavior of the SDK,
 * specifically in what its payload looks like.
 */
internal class EmbracePayloadAssertionInterface(
    bootstrapper: ModuleInitBootstrapper,
    private val apiServer: FakeApiServer?,
) {

    companion object {
        private const val WAIT_TIME_MS = 10000
    }

    private val serializer by lazy { bootstrapper.initModule.jsonSerializer }
    private val deliveryTracer by lazy {
        checkNotNull(bootstrapper.deliveryModule.deliveryTracer)
    }

    /*** LOGS ***/


    /**
     * Returns the list of log payload envelopes that have been sent. If [expectedSize] is specified,
     * it will wait a maximum of 1 second for the number of payloads that exist to equal
     * to that before returning, timing out if it doesn't.
     */
    internal fun getLogEnvelopes(expectedSize: Int) = retrieveLogEnvelopes(expectedSize)
    internal fun getSingleLogEnvelope() = getLogEnvelopes(1).single()

    /**
     * Returns the headers from log requests in the order they were received.
     */
    internal fun getLogRequestHeaders(): List<Map<String, String>> {
        return checkNotNull(apiServer).getLogRequestHeaders()
    }

    private fun retrieveLogEnvelopes(
        expectedSize: Int,
    ): List<Envelope<LogPayload>> {
        val supplier = { checkNotNull(apiServer).getLogEnvelopes() }
        try {
            return retrievePayload(expectedSize = expectedSize, supplier = supplier)
        } catch (exc: TimeoutException) {
            val envelopes: List<Map<String, String?>> = supplier().map { envelope ->
                mapOf(
                    "type" to envelope.type,
                    "logCount" to envelope.data.logs?.size.toString(),
                    "hashCodes" to envelope.data.logs?.map { it.hashCode() }?.joinToString { ", " }
                )
            }
            throwPayloadErrMsg(expectedSize, envelopes.size, envelopes, exc)
        }
    }


    /*** ATTACHMENTS ***/

    internal fun getAttachments(expectedSize: Int) = retrieveAttachments(expectedSize)
    internal fun getSingleAttachment() = getAttachments(1).single()

    private fun retrieveAttachments(
        expectedSize: Int,
    ): List<List<FormPart>> {
        val supplier = { checkNotNull(apiServer).getAttachments() }
        try {
            return retrievePayload(expectedSize = expectedSize, supplier = supplier)
        } catch (exc: TimeoutException) {
            throwPayloadErrMsg(expectedSize, supplier().size, emptyList(), exc)
        }
    }


    /*** SESSIONS ***/

    /**
     * Returns a list of sessions that were completed by the SDK.
     */
    internal fun getSessionEnvelopes(
        expectedSize: Int,
        state: ApplicationState = ApplicationState.FOREGROUND,
        waitTimeMs: Int = WAIT_TIME_MS,
    ): List<Envelope<SessionPayload>> {
        return retrieveSessionEnvelopes(expectedSize, state, waitTimeMs)
    }

    /**
     * Asserts a single session was completed by the SDK.
     */
    internal fun getSingleSessionEnvelope(
        state: ApplicationState = ApplicationState.FOREGROUND,
    ): Envelope<SessionPayload> = getSessionEnvelopes(1, state).single()

    private fun retrieveSessionEnvelopes(
        expectedSize: Int, appState: ApplicationState, waitTimeMs: Int,
    ): List<Envelope<SessionPayload>> {
        val supplier = {
            checkNotNull(apiServer).getSessionEnvelopes()
                .filter { it.findAppState() == appState }
        }
        try {
            return retrievePayload(expectedSize, waitTimeMs, supplier)
        } catch (exc: TimeoutException) {
            val envelopes = checkNotNull(apiServer).getSessionEnvelopes()
            val sessions: List<Map<String, String?>> = envelopes.map {
                mapOf(
                    "sessionId" to it.getSessionId(),
                    "cleanExit" to it.findSessionSpan().attributes?.findAttributeValue(embCleanExit.name),
                    "state" to it.findSessionSpan().attributes?.findAttributeValue(embState.name)
                )
            }
            throwPayloadErrMsg(expectedSize, envelopes.filter { it.findAppState() == appState }.size, sessions, exc)
        }
    }

    private fun Envelope<SessionPayload>.findAppState(): ApplicationState {
        val attrs = findSessionSpan().attributes
        val state = checkNotNull(attrs?.findAttributeValue(embState.name)) {
            "AppState not found in session payload."
        }
        val value = state.uppercase(Locale.ENGLISH)
        return ApplicationState.valueOf(value)
    }


    /*** Config ***/


    internal fun assertConfigRequested(expectedRequests: Int) {
        val supplier = {
            checkNotNull(apiServer).getConfigRequests()
        }
        try {
            retrievePayload(expectedSize = expectedRequests, supplier = supplier)
        } catch (exc: TimeoutException) {
            throw IllegalStateException(
                "Expected $expectedRequests config requests, but got ${supplier().size}.",
                exc
            )
        }
    }

    /**
     * Asserts that config was persisted on disk and returns the persisted information.
     */
    internal fun readPersistedConfigResponse(): ConfigHttpResponse {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val storageDir = File(ctx.filesDir, "embrace_remote_config")
        if (!storageDir.exists()) {
            throw IllegalStateException("Config storage directory does not exist.")
        }
        val responseFile = File(storageDir, "most_recent_response")
        if (!responseFile.exists()) {
            throw IllegalStateException("Config response file does not exist.")
        }
        val etagFile = File(storageDir, "etag")
        if (!etagFile.exists()) {
            throw IllegalStateException("Config etag file does not exist.")
        }
        val remoteConfig = readRemoteConfigFile(responseFile)
        return ConfigHttpResponse(remoteConfig, readEtagFile(etagFile))
    }

    private fun readRemoteConfigFile(file: File): RemoteConfig {
        try {
            return file.inputStream().buffered().use {
                serializer.fromJson(it, RemoteConfig::class.java)
            }
        } catch (exc: Throwable) {
            throw IllegalStateException("Failed to read remote config file.", exc)
        }
    }

    private fun readEtagFile(etagFile: File): String {
        try {
            return etagFile.readText()
        } catch (exc: Throwable) {
            throw IllegalStateException("Failed to read etag file for config.", exc)
        }
    }

    fun Envelope<SessionPayload>.assertDeadSessionResurrected(crashData: StoredNativeCrashData?) {
        with(checkNotNull(getSessionSpan())) {
            assertEquals(Span.Status.ERROR, status)

            if (crashData != null) {
                assertEquals(checkNotNull(crashData.sessionEnvelope).getSessionId(), getSessionId())
                assertEquals(crashData.lastHeartbeatMs, endTimeNanos?.nanosToMillis())
                assertEquals(
                    crashData.nativeCrash.nativeCrashId,
                    attributes?.findAttributeValue(embCrashId.name)
                )
            } else {
                assertNull(attributes?.findAttributeValue(embCrashId.name))
            }
        }
    }

    fun assertNativeCrashSent(
        log: Log,
        crashData: StoredNativeCrashData,
        symbolMap: Map<String, String>,
    ) {
        assertEquals("ERROR", log.severityText)
        assertEquals("", log.body)

        val symbols = serializer.toJson(
            symbolMap,
            TypeUtils.typedMap(String::class.java, String::class.java)
        )
        assertEquals(crashData.nativeCrash.timestamp, log.timeUnixNano?.nanosToMillis())

        val attrs = checkNotNull(log.attributes)
        attrs.assertMatches(
            mapOf(
                "emb.android.native_crash.exception" to crashData.nativeCrash.crash,
                "emb.android.native_crash.symbols" to symbols,
                "emb.private.send_mode" to "DEFER",
                "emb.type" to "sys.android.native_crash",
            )
        )
        assertNotNull(attrs.findAttributeValue("log.record.uid"))
        assertNotNull(attrs.findAttributeValue("emb.android.crash_number"))
        if (crashData.sessionEnvelope != null) {
            assertEquals(crashData.sessionEnvelope.getSessionId(), attrs.findAttributeValue("session.id"))
        }
        assertFalse(crashData.getCrashFile().exists())
    }

    /*** TEST INFRA ***/

    /**
     * Validates a payload against a golden file in the test resources. If the payload does not match
     * the golden file, the assertion fails.
     */
    internal fun <T> validatePayloadAgainstGoldenFile(
        payload: T,
        goldenFileName: String,
    ) {
        try {
            val observedJson = serializer.toJson(
                payload,
                Envelope.sessionEnvelopeType
            )
            val expectedJson = ResourceReader.readResourceAsText(goldenFileName)
            val result = JsonComparator.compare(JSONObject(expectedJson), JSONObject(observedJson))

            if (result.isNotEmpty()) {
                val msg by lazy {
                    "Request payload differed from expected JSON '$goldenFileName' due to following " +
                        "reasons: ${result.joinToString("; ")}\n" +
                        "Dump of full JSON: $observedJson"
                }
                Assert.fail(msg)
            }
        } catch (e: IOException) {
            throw IllegalStateException("Failed to validate request against golden file.", e)
        }
    }

    /**
     * Retrieves a payload that was stored in the delivery service.
     */
    private inline fun <reified T> retrievePayload(
        expectedSize: Int?,
        waitTimeMs: Int = WAIT_TIME_MS,
        supplier: () -> List<T>,
    ): List<T> {
        return when (expectedSize) {
            null -> supplier()
            else ->
                returnIfConditionMet(
                    waitTimeMs = waitTimeMs,
                    desiredValueSupplier = supplier,
                    dataProvider = supplier,
                    condition = { data ->
                        data.size == expectedSize
                    },
                    errorMessageSupplier = {
                        val payloads = supplier()
                        "Timeout. Expected $expectedSize payloads, but got ${payloads.size}."
                    }
                )
        }
    }

    private fun throwPayloadErrMsg(
        expectedSize: Int,
        observedSize: Int,
        envelopes: List<Map<String, String?>>,
        exc: TimeoutException,
    ): Nothing {
        throw IllegalStateException(
            "Expected $expectedSize envelopes, but got $observedSize matching criteria. " +
                "All received envelopes: $envelopes.\n${deliveryTracer.generateReport()}", exc
        )
    }
}
