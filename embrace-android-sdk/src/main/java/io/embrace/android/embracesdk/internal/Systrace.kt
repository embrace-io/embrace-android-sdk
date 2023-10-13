package io.embrace.android.embracesdk.internal

import android.os.Trace
import io.embrace.android.embracesdk.InternalApi

/**
 * Shim to add custom events to system traces if running in the applicable API versions. Basic alternative to using androidx.tracing.
 */
@InternalApi
internal class Systrace private constructor() {
    companion object {

        /**
         * Start a trace section. The name of the section will be [sectionName] prefixed by "emb-"
         */
        fun start(sectionName: String) {
            Trace.beginSection("emb-$sectionName")
        }

        /**
         * Close the last trace section that was started
         */
        fun end() {
            Trace.endSection()
        }

        /**
         * Create a trace section around the lambda passed in and return the result.
         * The name of the section will be [sectionName] prefixed by "emb-"
         *
         * Note: rethrowing the same [Throwable] that was caught is appropriate here because use of this should not change the code path.
         */
        @Suppress("RethrowCaughtException")
        inline fun <T> trace(sectionName: String, code: () -> T): T {
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
    }
}
