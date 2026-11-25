package io.embrace.android.embracesdk.benchmark.endpoint

import org.junit.rules.ExternalResource

/**
 * Runs a [LocalEmbraceEndpoint] serving [remoteConfig] around each test: started and warmed up before the test, so the
 * app the benchmark launches finds its endpoints and doesn't wait on the endpoint's first-request work, and closed
 * after it. Closing prints what it answered, which lands in logcat under `System.out`.
 */
class LocalEmbraceEndpointRule(
    configPort: Int = LOCAL_ENDPOINT_CONFIG_PORT,
    dataPort: Int = LOCAL_ENDPOINT_DATA_PORT,
    remoteConfig: String = REMOTE_CONFIG_JSON,
) : ExternalResource() {

    /** The endpoint this rule runs. */
    val endpoint: LocalEmbraceEndpoint = LocalEmbraceEndpoint(configPort, dataPort, remoteConfig)

    override fun before() {
        endpoint.start()
        endpoint.warmUp()
    }

    override fun after() {
        endpoint.close()
        println("local Embrace endpoint answered: ${endpoint.answered}")
    }
}
