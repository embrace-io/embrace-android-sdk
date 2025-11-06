package io.embrace.android.embracesdk.internal.instrumentation.okhttp

import io.embrace.android.embracesdk.internal.instrumentation.network.TraceparentGenerator

class FakeTraceparentGenerator(val traceparent: String) : TraceparentGenerator {
    override fun generateW3cTraceparent(): String = traceparent
}
