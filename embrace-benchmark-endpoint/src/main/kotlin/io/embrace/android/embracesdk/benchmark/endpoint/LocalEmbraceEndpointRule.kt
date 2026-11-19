package io.embrace.android.embracesdk.benchmark.endpoint

import org.junit.rules.ExternalResource

/**
 * Runs a [LocalEmbraceEndpoint] serving [remoteConfig] around each test: started before the test, so the app the
 * benchmark launches finds its endpoints, and closed after it. Closing prints what it answered, which lands in logcat
 * under `System.out`.
 */
class LocalEmbraceEndpointRule(
    port: Int = LOCAL_ENDPOINT_PORT,
    remoteConfig: String = REMOTE_CONFIG_JSON,
) : ExternalResource() {

    /** The endpoint this rule runs. */
    val endpoint: LocalEmbraceEndpoint = LocalEmbraceEndpoint(port, remoteConfig)

    override fun before() {
        endpoint.start()
    }

    override fun after() {
        endpoint.close()
        println("local Embrace endpoint answered: ${endpoint.answered}")
    }
}
