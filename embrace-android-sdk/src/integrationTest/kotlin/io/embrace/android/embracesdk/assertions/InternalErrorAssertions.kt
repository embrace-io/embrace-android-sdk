package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.opentelemetry.semconv.ExceptionAttributes
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
            log.attributes?.findAttributeValue("emb.type") == "sys.internal"
        }

    if (logs.isEmpty()) {
        fail("No internal errors found")
    }

    val matchingLogs = logs.filter { log ->
        val attrs = log.attributes
        attrs?.findAttributeValue(ExceptionAttributes.EXCEPTION_TYPE.key) == exceptionClassName &&
                attrs.findAttributeValue(ExceptionAttributes.EXCEPTION_MESSAGE.key) == errorMessage
    }
    if (matchingLogs.isEmpty()) {
        fail("No internal errors found matching the expected exception")
    }
}
