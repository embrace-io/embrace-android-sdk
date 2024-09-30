package io.embrace.android.embracesdk

import android.app.Activity
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.EventMessage
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.utils.Provider
import org.json.JSONObject
import org.junit.Assert
import org.robolectric.Robolectric
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/*** Extension functions that are syntactic sugar for retrieving information from the SDK. ***/


/*** LOGS ***/


/**
 * Returns the list of log payload envelopes that have been sent. If [expectedSize] is specified,
 * it will wait a maximum of 1 second for the number of payloads that exist to equal
 * to that before returning, timing out if it doesn't.
 */
internal fun IntegrationTestRule.Harness.getSentLogPayloads(expectedSize: Int? = null): List<Envelope<LogPayload>> {
    // TODO: future: avoid null expectedSize. Flaky because we can't predict when logs are
    //  batched & sent.
    return retrievePayload(expectedSize) {
        overriddenDeliveryModule.deliveryService.lastSentLogPayloads
    }
}

/**
 * Returns the list of log payload envelopes that have been stored. If [expectedSize] is specified,
 * it will wait a maximum of 1 second for the number of payloads that exist to equal to that
 * before returning, timing out if it doesn't.
 */
internal fun IntegrationTestRule.Harness.getStoredLogPayloads(expectedSize: Int): List<Envelope<LogPayload>> {
    return retrievePayload(expectedSize) {
        overriddenDeliveryModule.deliveryService.lastSavedLogPayloads
    }
}

/**
 * Returns the last log in a list of log payloads.
 */
internal fun List<Envelope<LogPayload>>.getLastLog(): Log {
    return checkNotNull(last().data.logs).last()
}


/*** MOMENTS ***/


/**
 * Returns the list of Moments that have been sent. If [expectedSize] is specified, it
 * will wait a maximum of 1 second for the number of payloads that exist to equal that before
 * returning, timing out if it doesn't.
 */
internal fun IntegrationTestRule.Harness.getSentMoments(expectedSize: Int): List<EventMessage> {
    return retrievePayload(expectedSize) {
        overriddenDeliveryModule.deliveryService.sentMoments
    }
}


/*** SESSIONS ***/


/**
 * Returns a list of session that were sent by the SDK since startup.
 */
internal fun IntegrationTestRule.Harness.getSentSessions(expectedSize: Int? = null): List<Envelope<SessionPayload>> {
    return retrievePayload(expectedSize) {
        overriddenDeliveryModule.deliveryService.getSentSessions()
    }
}

/**
 * Returns a list of background activity payloads that were sent by the SDK since startup.
 */
internal fun IntegrationTestRule.Harness.getSentBackgroundActivities(expectedSize: Int? = null): List<Envelope<SessionPayload>> {
    return retrievePayload(expectedSize) {
        overriddenDeliveryModule.deliveryService.getSentBackgroundActivities()
    }
}

/**
 * Returns a single session or throws.
 */
internal fun IntegrationTestRule.Harness.getSingleSession(): Envelope<SessionPayload> {
    return getSentSessions(1).single()
}

/**
 * Run some [action] and the validate the next saved background activity using
 * [validationFn]. If no background activity is saved with
 * 1 second, this fails.
 */
internal fun IntegrationTestRule.Harness.checkNextSavedBackgroundActivity(
    action: () -> Unit,
    validationFn: (Envelope<SessionPayload>) -> Unit
): Envelope<SessionPayload> =
    with(overriddenDeliveryModule.deliveryService) {
        checkNextSavedSessionEnvelope(
            dataProvider = ::getSavedBackgroundActivities,
            action = action,
            validationFn = validationFn,
        )
    }

private fun IntegrationTestRule.Harness.checkNextSavedSessionEnvelope(
    dataProvider: () -> List<Envelope<SessionPayload>>,
    action: () -> Unit,
    validationFn: (Envelope<SessionPayload>) -> Unit
): Envelope<SessionPayload> {
    val startingSize = dataProvider().size
    overriddenClock.tick(10_000L)
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

/**
 * Starts & ends a session for the purposes of testing. An action can be supplied as a lambda
 * parameter: any code inside the lambda will be executed, so can be used to add breadcrumbs,
 * send log messages etc, while the session is active. The end session message is returned so
 * that the caller can perform further assertions if needed.
 *
 * This function fakes the lifecycle events that trigger a session start & end. The session
 * should always be 30s long. Additionally, it performs assertions against fields that
 * are guaranteed not to change in the start/end message.
 */
internal fun IntegrationTestRule.Harness.recordSession(
    simulateActivityCreation: Boolean = false,
    action: () -> Unit = {}
) {
    // get the activity service & simulate the lifecycle event that triggers a new session.
    val activityService = checkNotNull(Embrace.getImpl().activityService)
    val activityController =
        if (simulateActivityCreation) Robolectric.buildActivity(Activity::class.java) else null

    activityController?.create()
    activityController?.start()
    activityService.onForeground()
    activityController?.resume()

    // perform a custom action during the session boundary, e.g. adding a breadcrumb.
    action()

    // end session 30s later by entering background
    overriddenClock.tick(30000)
    activityController?.pause()
    activityController?.stop()
    activityService.onBackground()
}


/*** TEST INFRA ***/


/**
 * Validates a payload against a golden file in the test resources. If the payload does not match
 * the golden file, the assertion fails.
 */
internal fun <T> IntegrationTestRule.validatePayloadAgainstGoldenFile(
    payload: T,
    goldenFileName: String
) {
    try {
        val observedJson = harness.overriddenInitModule.jsonSerializer.toJson(
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
private inline fun <reified T> IntegrationTestRule.Harness.retrievePayload(
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
internal inline fun <T, R> returnIfConditionMet(
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

private const val CHECK_INTERVAL_MS: Int = 10
