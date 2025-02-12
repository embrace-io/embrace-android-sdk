package io.embrace.android.gradle.plugin.system

class SystemWrapper {
    fun getProperty(key: String): String? {
        return try {
            System.getProperty(key)
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun getenv(name: String): String? {
        return try {
            System.getenv(name)
        } catch (e: SecurityException) {
            null
        }
    }
}
