package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.TraceparentInjectionBehavior

class FakeTraceparentInjectionBehavior(
    var traceparentInjectionEnabled: Boolean = false,
    var hostCheckSucceeds: Boolean = true,
) : TraceparentInjectionBehavior {

    override fun isTraceparentInjectionEnabled(): Boolean = traceparentInjectionEnabled

    override fun shouldInjectTraceparent(host: String?): Boolean = traceparentInjectionEnabled && hostCheckSucceeds
}
