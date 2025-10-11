package io.embrace.android.embracesdk.internal.utils

import android.annotation.SuppressLint
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Trace
import androidx.annotation.ChecksSdkIntAtLeast
import kotlin.random.Random

/**
 * Shim to add custom events to system traces for API 29+. Basic alternative to using androidx.tracing that is safe to interleave.
 */
object EmbTrace {
    data class Instance(val name: String, val id: Int)

    /**
     * Start a system trace section and return an instance identifier that is required to close it.
     * The name of the section in the logged trace will be [name] prefixed by "emb-".
     * If the application is running on version earlier than [VERSION_CODES.Q], no trace section will be started.
     */
    fun startAsync(name: String): Instance? {
        return if (enabled()) {
            val instance = Instance("emb-$name".take(127), Random.nextInt())
            Trace.beginAsyncSection(instance.name, instance.id)
            instance
        } else {
            null
        }
    }

    /**
     * Close trace section identified by [instance]
     */
    fun endAsync(instance: Instance) {
        if (enabled()) {
            Trace.endAsyncSection(instance.name, instance.id)
        }
    }

    /**
     * Start a synchronous trace. If there is an in-progress synchronous trace, this will be nested underneath it.
     *
     * Note: this must be manually ended by calling [end]
     */
    @SuppressLint("UnclosedTrace")
    fun start(name: String) {
        if (enabled()) {
            Trace.beginSection("emb-$name".take(127))
        }
    }

    /**
     * Stop the last-started synchronous trace.
     */
    fun end() {
        if (enabled()) {
            Trace.endSection()
        }
    }

    /**
     * Create a trace section around the lambda passed in and return the result.
     * The name of the section will be [sectionName] prefixed by "emb-"
     *
     * Note: rethrowing the same [Throwable] that was caught is appropriate here because use of this should not change the code path.
     */
    @Suppress("RethrowCaughtException")
    inline fun <T> traceAsync(sectionName: String, code: Provider<T>): T {
        val returnValue: T
        var instance: Instance? = null
        try {
            instance = startAsync(sectionName)
            returnValue = code()
        } catch (t: Throwable) {
            throw t
        } finally {
            instance?.let { endAsync(it) }
        }

        return returnValue
    }

    /**
     * Create a trace section around the lambda passed in and return the result.
     * The name of the section will be [sectionName] prefixed by "emb-"
     *
     * Note: rethrowing the same [Throwable] that was caught is appropriate here because use of this should not change the code path.
     */
    @Suppress("RethrowCaughtException")
    inline fun <T> trace(sectionName: String, code: Provider<T>): T {
        val returnValue: T
        try {
            start(sectionName)
            returnValue = code()
        } catch (t: Throwable) {
            throw t
        } finally {
            end()
        }

        return returnValue
    }

    @ChecksSdkIntAtLeast(api = VERSION_CODES.Q)
    private fun enabled(): Boolean = Build.VERSION.SDK_INT >= VERSION_CODES.Q
}
