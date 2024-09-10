package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.StartupBehavior

class FakeStartupBehavior(
    private val automaticEndEnabled: Boolean = true
) : StartupBehavior {

    override fun isAutomaticEndEnabled(): Boolean = automaticEndEnabled
}
