package io.embrace.android.embracesdk.internal.instrumentation.network

internal class FakeHttpOverrideRequest(
    private val headerByName: String? = null,
    private val urlString: String = "https://fakeurl.fake",
    private val overriddenUrlStringProvider: (pathOverride: String) -> String = { urlString },
) : HttpPathOverrideRequest {
    override fun getHeaderByName(name: String): String? = headerByName

    override fun getOverriddenURL(pathOverride: String): String = overriddenUrlStringProvider.invoke(pathOverride)

    override fun getURLString(): String = urlString
}
