package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.payload.LegacyExceptionError
import io.embrace.android.embracesdk.IntegrationTestRule
import org.junit.Assert.assertTrue

/**
 * Return true if at least one exception matching the expected time, exception type, and error message is found in the internal errors
 */
internal fun assertInternalErrorLogged(
    exceptionError: LegacyExceptionError?,
    exceptionClassName: String,
    errorMessage: String,
    errorTimeMs: Long = IntegrationTestRule.DEFAULT_SDK_START_TIME_MS
) {
    requireNotNull(exceptionError) { "No internal errors found" }
    var foundErrorMatch = false
    var foundErrorAtTime = false
    val unmatchedDetails: MutableList<String> = mutableListOf()
    val errors = exceptionError.exceptionErrors.toList()
    assertTrue("No exception errors found", errors.isNotEmpty())
    errors.forEach { error ->
        if (errorTimeMs == error.timestamp) {
            foundErrorAtTime = true
            val firstExceptionInfo = checkNotNull(error.exceptions).first()
            with(firstExceptionInfo) {
                if (exceptionClassName == name && errorMessage == message) {
                    foundErrorMatch = true
                } else {
                    unmatchedDetails.add("'$exceptionClassName' is not '$name' OR '$errorMessage' is not '$message' \n")
                }
            }
        }
    }

    assertTrue("No internal error found matching the expected time", foundErrorAtTime)

    assertTrue(
        "Expected exception not found. " +
            "Found following ${unmatchedDetails.size} exceptions in ${errors.size} errors instead: $unmatchedDetails",
        foundErrorMatch
    )
}
