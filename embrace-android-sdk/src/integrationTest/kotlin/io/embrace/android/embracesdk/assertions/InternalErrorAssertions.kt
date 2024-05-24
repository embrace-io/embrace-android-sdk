package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.findLogAttribute
import io.embrace.android.embracesdk.injection.ModuleInitBootstrapper
import org.junit.Assert.fail

/**
 * Return true if at least one exception matching the expected time, exception type, and error message is found in the internal errors
 */
internal fun assertInternalErrorLogged(
    bootstrapper: ModuleInitBootstrapper,
    exceptionClassName: String,
    errorMessage: String
) {
    bootstrapper.customerLogModule.logOrchestrator.flush(false)
    val deliveryService = bootstrapper.deliveryModule.deliveryService as FakeDeliveryService
    val logs = deliveryService.lastSentLogPayloads.mapNotNull { it.data.logs }
        .flatten()
        .filter { log ->
            log.findLogAttribute("emb.type") == "sys.internal"
        }

    if (logs.isEmpty()) {
        fail("No internal errors found")
    }

    val matchingLogs = logs.filter { log ->
        log.findLogAttribute("exception.type") == exceptionClassName &&
            log.findLogAttribute("exception.message") == errorMessage
    }
    if (matchingLogs.isEmpty()) {
        fail("No internal errors found matching the expected exception")
    }
}
