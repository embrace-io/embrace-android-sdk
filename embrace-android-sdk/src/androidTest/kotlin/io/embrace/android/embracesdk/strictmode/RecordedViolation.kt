package io.embrace.android.embracesdk.strictmode

import android.os.strictmode.Violation

internal class RecordedViolation(
    val violation: Violation,
    val threadName: String,
) {

    /**
     * The top-most SDK frame that triggered this violation, as `Class.method`. Line numbers are
     * excluded so unrelated edits don't invalidate [KNOWN_VIOLATIONS]. Null if no SDK frame was
     * involved, i.e. the framework or the test harness did it and we don't assert on it.
     */
    val signature: String? = violation.stackTrace
        .firstOrNull { it.className.startsWith(SDK_PACKAGE) && !it.className.startsWith(TEST_PACKAGE) }
        ?.let { "${it.className.removePrefix(SDK_CLASS_PREFIX)}.${it.methodName}" }

    val violationName: String get() = violation.javaClass.simpleName

    fun describe(): String = "$violationName on '$threadName' at ${signature ?: "<no SDK frame>"}"

    fun describeWithStack(): String = "${describe()}\n${violation.stackTraceToString()}"

    private companion object {
        const val SDK_PACKAGE = "io.embrace."
        const val SDK_CLASS_PREFIX = "io.embrace.android.embracesdk."
        const val TEST_PACKAGE = "io.embrace.android.embracesdk.strictmode."
    }
}
