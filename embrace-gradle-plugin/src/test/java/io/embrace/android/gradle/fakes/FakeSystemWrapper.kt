package io.embrace.android.gradle.fakes

import io.embrace.android.gradle.plugin.system.SystemWrapper

class FakeSystemWrapper : SystemWrapper {
    private val properties = mutableMapOf<String, String>()
    private val env = mutableMapOf<String, String>()

    fun setEnvironmentVariable(name: String, value: String) {
        env[name] = value
    }

    override fun getProperty(key: String): String? = properties[key]

    override fun getEnvironmentVariable(name: String): String? = env[name]
}
