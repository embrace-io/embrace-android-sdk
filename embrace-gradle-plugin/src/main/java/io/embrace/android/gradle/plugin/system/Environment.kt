package io.embrace.android.gradle.plugin.system

/**
 * It is a wrapper class to get environment variables.
 */
fun interface Environment {
    /**
     * It gets an environment variable.
     */
    fun getVariable(name: String): String?
}
