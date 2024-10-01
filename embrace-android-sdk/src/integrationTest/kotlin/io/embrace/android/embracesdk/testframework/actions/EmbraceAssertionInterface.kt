package io.embrace.android.embracesdk.testframework.actions

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.testframework.assertions.JsonComparator
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakePayloadStore
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.utils.Provider
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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
    private val payloadStore by lazy { bootstrapper.deliveryModule.payloadStore as FakePayloadStore }
    private val clock by lazy { bootstrapper.initModule.clock as FakeClock }
    private val serializer by lazy { bootstrapper.initModule.jsonSerializer }


    /*** LOGS ***/


    /**
     * Returns the list of log payload envelopes that have been sent. If [expectedSize] is specified,
     * it will wait a maximum of 1 second for the number of payloads that exist to equal
     * to that before returning, timing out if it doesn't.
     */
    internal fun getSentLogPayloads(expectedSize: Int? = null): List<Envelope<LogPayload>> {
        // TODO: future: avoid null expectedSize. Flaky because we can't predict when logs are
        //  batched & sent.
        return retrieveLogPayloads(expectedSize, true)
    }

    /**
     * Returns the list of log payload envelopes that have been stored. If [expectedSize] is specified,
     * it will wait a maximum of 1 second for the number of payloads that exist to equal to that
     * before returning, timing out if it doesn't.
     */
    internal fun getStoredLogPayloads(expectedSize: Int): List<Envelope<LogPayload>> {
        return retrieveLogPayloads(expectedSize, false)
    }

    private fun retrieveLogPayloads(
        expectedSize: Int?,
        sent: Boolean
    ): List<Envelope<LogPayload>> {
        return retrievePayload(expectedSize) {
            payloadStore.storedLogPayloads.filter { it.second == sent }.map { it.first }
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
     * Returns a list of session that were sent by the SDK since startup.
     */
    internal fun getSentSessions(expectedSize: Int? = null): List<Envelope<SessionPayload>> {
        return retrieveSessionPayloads(expectedSize, ApplicationState.FOREGROUND)
    }

    /**
     * Returns a list of background activity payloads that were sent by the SDK since startup.
     */
    internal fun getSentBackgroundActivities(expectedSize: Int? = null): List<Envelope<SessionPayload>> {
        return retrieveSessionPayloads(expectedSize, ApplicationState.BACKGROUND)
    }

    /**
     * Returns a single session or throws.
     */
    internal fun getSingleSession(): Envelope<SessionPayload> {
        return getSentSessions(1).single()
    }

    private fun retrieveSessionPayloads(
        expectedSize: Int?, appState: ApplicationState
    ): List<Envelope<SessionPayload>> {
        return retrievePayload(expectedSize) {
            val sessions = payloadStore.storedSessionPayloads.map { it.first }
            sessions.filter { it.findAppState() == appState }
        }
    }

    private fun Envelope<SessionPayload>.findAppState(): ApplicationState {
        val state = checkNotNull(findSessionSpan().attributes?.findAttributeValue(embState.name)) {
            "AppState not found in session payload."
        }
        val value = state.uppercase(Locale.ENGLISH)
        return ApplicationState.valueOf(value)
    }

    /**
     * Run some [action] and the validate the next saved background activity using
     * [validationFn]. If no background activity is saved with
     * 1 second, this fails.
     */
    internal fun checkNextSavedBackgroundActivity(
        action: () -> Unit,
        validationFn: (Envelope<SessionPayload>) -> Unit
    ): Envelope<SessionPayload> =
        with(deliveryService) {
            checkNextSavedSessionEnvelope(
                dataProvider = ::getSavedBackgroundActivities,
                action = action,
                validationFn = validationFn,
            )
        }

    private fun checkNextSavedSessionEnvelope(
        dataProvider: () -> List<Envelope<SessionPayload>>,
        action: () -> Unit,
        validationFn: (Envelope<SessionPayload>) -> Unit
    ): Envelope<SessionPayload> {
        val startingSize = dataProvider().size
        clock.tick(10_000L)
        action()
        return when (dataProvider().size) {
            startingSize -> {
                returnIfConditionMet(
                    desiredValueSupplier = {
                        dataProvider().getNth(startingSize)
                    },
                    condition = { data ->
                        data.size > startingSize
                    },
                    dataProvider = dataProvider
                )
            }

            else -> {
                dataProvider().getNth(startingSize)
            }
        }.apply {
            validationFn(this)
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

    private fun <T> List<T>.getNth(n: Int) = filterIndexed { index, _ -> index == n }.single()

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
                    }
                )
        }
    }

    /**
     * Return the result of [desiredValueSupplier] if [condition] is true before [waitTimeMs]
     * elapses. Otherwise, throws [TimeoutException]
     */
    private inline fun <T, R> returnIfConditionMet(
        desiredValueSupplier: Provider<T>,
        waitTimeMs: Int = 1000,
        dataProvider: () -> R,
        condition: (R) -> Boolean
    ): T {
        val tries: Int = waitTimeMs / CHECK_INTERVAL_MS
        val countDownLatch = CountDownLatch(1)

        repeat(tries) {
            if (!condition(dataProvider())) {
                countDownLatch.await(CHECK_INTERVAL_MS.toLong(), TimeUnit.MILLISECONDS)
            } else {
                return desiredValueSupplier.invoke()
            }
        }

        throw TimeoutException("Timeout period elapsed before condition met.")
    }

    companion object {
        private const val CHECK_INTERVAL_MS: Int = 10
    }
}