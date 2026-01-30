package io.embrace.android.embracesdk

import org.robolectric.annotation.Config
import org.robolectric.pluginapi.config.GlobalConfigProvider

class EmbraceGlobalConfigProvider : GlobalConfigProvider {
    override fun get(): Config {
        return Config.Builder().setSdk(35).build()
    }
}
