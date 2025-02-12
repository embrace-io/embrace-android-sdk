package io.embrace.android.gradle.fakes

import io.embrace.android.gradle.plugin.system.Environment
import java.util.concurrent.ConcurrentHashMap

class FakeEnvironment(initialVariables: Map<String, String> = emptyMap()) : Environment {

    val envVariables = ConcurrentHashMap<String, String>().apply {
        putAll(initialVariables)
    }

    override fun getVariable(name: String): String? = envVariables[name]
}
