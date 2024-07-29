package io.embrace.android.embracesdk.internal

import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Trace
import androidx.annotation.ChecksSdkIntAtLeast
import io.embrace.android.embracesdk.internal.utils.Provider
import kotlin.random.Random

/**
 * Shim to add custom events to system traces for API 29+. Basic alternative to using androidx.tracing that is safe to interleave.
 */
public object Systrace {
    public data class Instance(val name: String, val id: Int)

    /**
     * Start a system trace section and return an instance identifier that is required to close it.
     * The name of the section in the logged trace will be [name] prefixed by "emb-".
     * If the application is running on version earlier than [VERSION_CODES.Q], no trace section will be started.
     */
    @JvmStatic
    public fun start(name: String): Instance? {
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
    @JvmStatic
    public fun end(instance: Instance) {
        if (enabled()) {
            Trace.endAsyncSection(instance.name, instance.id)
        }
    }

    /**
     * Start a synchronous trace. If there is an in-progress synchronous trace, this will be nested underneath it.
     *
     * Note: this must be manually ended by calling [endSynchronous]
     */
    @JvmStatic
    public fun startSynchronous(name: String) {
        if (enabled()) {
            Trace.beginSection("emb-$name".take(127))
        }
    }

    /**
     * Stop the last-started synchronous trace.
     */
    @JvmStatic
    public fun endSynchronous() {
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
    @JvmStatic
    @Suppress("RethrowCaughtException")
    public inline fun <T> trace(sectionName: String, code: Provider<T>): T {
        val returnValue: T
        var instance: Instance? = null
        try {
            instance = start(sectionName)
            returnValue = code()
        } catch (t: Throwable) {
            throw t
        } finally {
            instance?.let { end(it) }
        }

        return returnValue
    }

    /**
     * Create a trace section around the lambda passed in and return the result.
     * The name of the section will be [sectionName] prefixed by "emb-"
     *
     * Note: rethrowing the same [Throwable] that was caught is appropriate here because use of this should not change the code path.
     */
    @JvmStatic
    @Suppress("RethrowCaughtException")
    public inline fun <T> traceSynchronous(sectionName: String, code: Provider<T>): T {
        val returnValue: T
        try {
            startSynchronous(sectionName)
            returnValue = code()
        } catch (t: Throwable) {
            throw t
        } finally {
            endSynchronous()
        }

        return returnValue
    }

    @ChecksSdkIntAtLeast(api = VERSION_CODES.Q)
    private fun enabled(): Boolean = Build.VERSION.SDK_INT >= VERSION_CODES.Q
}
