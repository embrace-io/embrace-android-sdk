package io.embrace.android.embracesdk.internal.otel.state

/**
 * A [StateStamp] that counts how many times its plain attributes are built, which is what a log that nothing of the app's
 * receives must not do. Network is wifi and the screen is Backgrounded, a value the SDK generated, as of the second change,
 * unless it is given another.
 */
internal class CountingStateStamp(
    private val stamp: StateStamp = FakeStateStamp(2, mapOf("network" to "wifi", "screen" to "~Backgrounded")),
) : StateStamp by stamp {

    var attributesBuilt = 0
        private set

    override val attributes: Map<String, String>
        get() = stamp.attributes.also { attributesBuilt++ }
}
