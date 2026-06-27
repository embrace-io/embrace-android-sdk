package io.embrace.android.embracesdk

import org.robolectric.annotation.Config
import org.robolectric.pluginapi.config.GlobalConfigProvider

class EmbraceGlobalConfigProvider : GlobalConfigProvider {
    override fun get(): Config {
        return Config.Builder().setSdk(Config.NEWEST_SDK).build()
    }
}
