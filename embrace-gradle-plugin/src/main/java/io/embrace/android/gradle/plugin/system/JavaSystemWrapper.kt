package io.embrace.android.gradle.plugin.system

interface SystemWrapper {
    fun getProperty(key: String): String?
    fun getEnvironmentVariable(name: String): String?
}

class JavaSystemWrapper : SystemWrapper {
    override fun getProperty(key: String): String? {
        return try {
            System.getProperty(key)
        } catch (_: Exception) {
            null
        }
    }

    override fun getEnvironmentVariable(name: String): String? {
        return try {
            System.getenv(name)
        } catch (_: Exception) {
            null
        }
    }
}
