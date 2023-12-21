package io.embrace.android.embracesdk

import android.app.Activity
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.payload.BackgroundActivityMessage
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.SessionMessage
import org.junit.Assert.assertEquals
import org.robolectric.Robolectric
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertNotNull

/*** Extension functions that are syntactic sugar for retrieving information from the SDK. ***/

/**
 * Returns a list of [EventMessage] logs that were sent by the SDK since startup. If [expectedSize] is specified, it will wait up to
 * 1 second to validate the number of sent log message equal that size. If a second passes that the size requirement is not met, a
 * [TimeoutException] will be thrown. If [expectedSize] is null or not specified, the correct sent log messages will be returned right
 * away.
 */
internal fun IntegrationTestRule.Harness.getSentLogMessages(expectedSize: Int? = null): List<EventMessage> {
    val logs = fakeDeliveryModule.deliveryService.lastSentLogs
    return when (expectedSize) {
        null -> logs
        else -> returnIfConditionMet({ logs }) {
            logs.size == expectedSize
        }
    }
}

/**
 * Returns the last [EventMessage] log that was sent by the SDK. If [expectedSize] is specified, it will wait up to 1 second to validate
 * the number of sent log message equal that size. If a second passes that the size requirement is not met, a [TimeoutException] will
 * be thrown. If [expectedSize] is null or not specified, the correct sent log messages will be returned right away.
 */
internal fun IntegrationTestRule.Harness.getLastSentLogMessage(expectedSize: Int? = null): EventMessage {
    return getSentLogMessages(expectedSize).last()
}

/**
 * Returns a list of [SessionMessage] that were sent by the SDK since startup.
 */
internal fun IntegrationTestRule.Harness.getSentSessionMessages(): List<SessionMessage> {
    return fakeDeliveryModule.deliveryService.lastSentSessions.map { it.first }
}

/**
 * Returns a list of [BackgroundActivityMessage] that were sent by the SDK since startup.
 */
internal fun IntegrationTestRule.Harness.getSentBackgroundActivities(): List<BackgroundActivityMessage> {
    return fakeDeliveryModule.deliveryService.lastSentBackgroundActivities
}

/**
 * Returns the last [SessionMessage] that was saved by the SDK.
 */
internal fun IntegrationTestRule.Harness.getLastSavedSessionMessage(): SessionMessage? {
    return fakeDeliveryModule.deliveryService.lastSavedSession
}

/**
 * Returns the last [SessionMessage] that was sent by the SDK.
 */
internal fun IntegrationTestRule.Harness.getLastSentSessionMessage(): SessionMessage? {
    return getSentSessionMessages().lastOrNull()
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
    simulateAppStartup: Boolean = false,
    action: () -> Unit = {}
): SessionMessage? {
    // get the activity service & simulate the lifecycle event that triggers a new session.
    val activityService = checkNotNull(Embrace.getImpl().activityService)
    val activityController =
        if (simulateAppStartup) Robolectric.buildActivity(Activity::class.java) else null

    activityController?.create()
    activityController?.start()
    activityService.onForeground()
    activityController?.resume()

    // perform a custom action during the session boundary, e.g. adding a breadcrumb.
    action()

    // end session 30s later by entering background
    fakeClock.tick(30000)
    activityController?.pause()
    activityService.onBackground()
    activityController?.stop()
    return getLastSentSessionMessage()
}

internal fun exceptionsService(): EmbraceInternalErrorService? = Embrace.getImpl().exceptionsService

/**
 * Return the result of [desiredValueSupplier] if [condition] is true before [waitTimeMs] elapses. Otherwise, throws [TimeoutException]
 */
internal fun <T> returnIfConditionMet(desiredValueSupplier: () -> T, waitTimeMs: Int = 1000, condition: () -> Boolean): T {
    val tries: Int = waitTimeMs / CHECK_INTERVAL_MS
    val countDownLatch = CountDownLatch(1)

    repeat(tries) {
        if (!condition()) {
            countDownLatch.await(CHECK_INTERVAL_MS.toLong(), TimeUnit.MILLISECONDS)
        } else {
            return desiredValueSupplier.invoke()
        }
    }

    throw TimeoutException("Timeout period elapsed before condition met")
}

internal fun verifySessionHappened(message: SessionMessage) {
    verifySessionMessage(message)
    assertEquals("en", message.session.messageType)
}

internal fun verifySessionMessage(sessionMessage: SessionMessage) {
    assertNotNull(sessionMessage.session)
    assertNotNull(sessionMessage.appInfo)
    assertNotNull(sessionMessage.deviceInfo)

    if (sessionMessage.session.messageType == "en") {
        assertNotNull(sessionMessage.userInfo)
        assertNotNull(sessionMessage.breadcrumbs)
        assertNotNull(sessionMessage.performanceInfo)
    }
}

internal fun verifyBgActivityHappened(message: BackgroundActivityMessage) {
    verifyBgActivityMessage(message)
    assertEquals("en", message.backgroundActivity.messageType)
}

internal fun verifyBgActivityMessage(message: BackgroundActivityMessage) {
    assertNotNull(message.backgroundActivity)
    assertNotNull(message.appInfo)
    assertNotNull(message.deviceInfo)

    if (message.backgroundActivity.messageType == "en") {
        assertNotNull(message.userInfo)
        assertNotNull(message.breadcrumbs)
        assertNotNull(message.performanceInfo)
    }
}

private const val CHECK_INTERVAL_MS: Int = 10
