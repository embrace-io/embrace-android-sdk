@file:Suppress("Filename", "MatchingDeclarationName")

package io.embrace.android.gradle.plugin

import org.gradle.api.logging.Logging

/**
 * Embrace-specific logger that automatically adds plugin and class prefixes.
 */
class EmbraceLogger {
    private val gradleLogger: org.gradle.api.logging.Logger
    private val logPrefix: String

    constructor(clazz: Class<*>) {
        gradleLogger = Logging.getLogger(clazz)
        logPrefix = "[EmbraceGradlePlugin] [${clazz.simpleName}]"
    }

    constructor(componentName: String) {
        gradleLogger = Logging.getLogger(componentName)
        logPrefix = "[EmbraceGradlePlugin] [$componentName]"
    }

    /**
     * Log a message with INFO severity.
     */
    fun info(msg: String, throwable: Throwable? = null) {
        gradleLogger.info("$logPrefix $msg", throwable)
    }

    /**
     * Log a message with DEBUG severity.
     */
    fun debug(msg: String, throwable: Throwable? = null) {
        gradleLogger.debug("$logPrefix $msg", throwable)
    }

    /**
     * Log a message with WARN severity.
     */
    fun warn(msg: String, throwable: Throwable? = null) {
        gradleLogger.warn("$logPrefix $msg", throwable)
    }

    /**
     * Log a message with ERROR severity.
     */
    fun error(msg: String, throwable: Throwable? = null) {
        gradleLogger.error("$logPrefix $msg", throwable)
    }
}
