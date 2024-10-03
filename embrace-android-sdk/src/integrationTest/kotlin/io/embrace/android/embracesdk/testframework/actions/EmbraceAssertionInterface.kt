package io.embrace.android.embracesdk.testframework.actions

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.testframework.assertions.JsonComparator
import java.io.IOException
import java.util.Locale
import org.json.JSONObject
import org.junit.Assert

/**
 * Provides assertions that can be used in integration tests to validate the behavior of the SDK,
 * specifically in what its payload looks like.
 */
internal class EmbraceAssertionInterface(
    bootstrapper: ModuleInitBootstrapper
) {

    private val deliveryService by lazy { bootstrapper.deliveryModule.deliveryService as FakeDeliveryService }
    private val requestExecutionService by lazy { bootstrapper.deliveryModule.requestExecutionService as FakeRequestExecutionService }
    private val serializer by lazy { bootstrapper.initModule.jsonSerializer }


    /*** LOGS ***/


    /**
     * Returns the list of log payload envelopes that have been sent. If [expectedSize] is specified,
     * it will wait a maximum of 1 second for the number of payloads that exist to equal
     * to that before returning, timing out if it doesn't.
     */
    internal fun getLogEnvelopes(expectedSize: Int): List<Envelope<LogPayload>> {
        return retrieveLogEnvelopes(expectedSize)
    }

    internal fun getSingleLogEnvelope(): Envelope<LogPayload> {
        return getLogEnvelopes(1).single()
    }

    private fun retrieveLogEnvelopes(
        expectedSize: Int
    ): List<Envelope<LogPayload>> {
        return retrievePayload(expectedSize) {
            requestExecutionService.getRequests<LogPayload>()
        }
    }


    /*** MOMENTS ***/


    /**
     * Returns the list of Moments that have been sent. If [expectedSize] is specified, it
     * will wait a maximum of 1 second for the number of payloads that exist to equal that before
     * returning, timing out if it doesn't.
     */
    internal fun getSentMoments(expectedSize: Int): List<EventMessage> {
        return retrievePayload(expectedSize) {
            deliveryService.sentMoments
        }
    }


    /*** SESSIONS ***/


    /**
     * Returns a list of sessions that were completed by the SDK.
     */
    internal fun getSessionEnvelopes(
        expectedSize: Int,
        state: ApplicationState = ApplicationState.FOREGROUND
    ): List<Envelope<SessionPayload>> {
        return retrieveSessionEnvelopes(expectedSize, state)
    }

    /**
     * Asserts a single session was completed by the SDK.
     */
    internal fun getSingleSessionEnvelope(
        state: ApplicationState = ApplicationState.FOREGROUND
    ): Envelope<SessionPayload> = getSessionEnvelopes(1, state).single()

    private fun retrieveSessionEnvelopes(
        expectedSize: Int, appState: ApplicationState
    ): List<Envelope<SessionPayload>> {
        return retrievePayload(expectedSize) {
            requestExecutionService.getRequests<SessionPayload>()
                .filter { it.findAppState() == appState }
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

    fun getCachedSessionEnvelopes(
        expectedSize: Int,
        appState: ApplicationState = ApplicationState.FOREGROUND
    ): List<Envelope<SessionPayload>> {
        return retrievePayload(expectedSize) {
            checkNotNull(deliveryService.savedSessionEnvelopes).map { it.first }
                .filter { it.findAppState() == appState }
        }
    }


    /*** TEST INFRA ***/


    /**
     * Validates a payload against a golden file in the test resources. If the payload does not match
     * the golden file, the assertion fails.
     */
    internal fun <T> validatePayloadAgainstGoldenFile(
        payload: T,
        goldenFileName: String
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
        supplier: () -> List<T>
    ): List<T> {
        return when (expectedSize) {
            null -> supplier()
            else ->
                returnIfConditionMet(
                    desiredValueSupplier = supplier,
                    dataProvider = supplier,
                    condition = { data ->
                        data.size == expectedSize
                    },
                    errorMessageSupplier = {
                        "Timeout. Expected $expectedSize payloads, but got ${supplier().size}."
                    }
                )
        }
    }
}