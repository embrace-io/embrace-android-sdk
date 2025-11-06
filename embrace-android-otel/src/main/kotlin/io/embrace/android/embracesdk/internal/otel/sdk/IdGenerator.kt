package io.embrace.android.embracesdk.internal.otel.sdk

import kotlin.random.Random

class IdGenerator(
    private val random: Random = Random.Default,
) {

    internal fun generateSpanId(): String = buildString(16) {
        appendHex(validRandomLong())
    }

    private fun validRandomLong(): Long {
        var value: Long
        do {
            value = random.nextLong()
        } while (value == 0L)
        return value
    }

    private fun StringBuilder.appendHex(value: Long) {
        append(value.toULong().toString(16).padStart(16, '0'))
    }

    companion object {
        private val INSTANCE = IdGenerator()

        fun generateLaunchInstanceId(): String = INSTANCE.generateSpanId()
    }
}
