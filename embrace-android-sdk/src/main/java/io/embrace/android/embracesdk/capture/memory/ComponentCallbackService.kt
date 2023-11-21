package io.embrace.android.embracesdk.capture.memory

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import java.io.Closeable

internal class ComponentCallbackService(
    private val application: Application,
    private val memoryService: MemoryService
) : ComponentCallbacks2, Closeable {

    init {
        application.applicationContext.registerComponentCallbacks(this)
    }

    /**
     * Called when the OS has determined that it is a good time for a process to trim unneeded
     * memory.
     *
     * @param trimLevel the context of the trim, giving a hint of the amount of trimming.
     */
    override fun onTrimMemory(trimLevel: Int) {
        InternalStaticEmbraceLogger.logDeveloper(
            "ComponentCallbackService",
            "onTrimMemory(). TrimLevel: $trimLevel"
        )
        if (trimLevel == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            try {
                memoryService.onMemoryWarning()
            } catch (ex: Exception) {
                InternalStaticEmbraceLogger.logDebug(
                    "Failed to handle onTrimMemory (low memory) event",
                    ex
                )
            }
        }
    }

    override fun onConfigurationChanged(configuration: Configuration) {}
    override fun onLowMemory() {}

    override fun close() {
        try {
            application.applicationContext.unregisterComponentCallbacks(this)
        } catch (ex: Exception) {
            InternalStaticEmbraceLogger.logDebug(
                "Error when closing ComponentCallbackService",
                ex
            )
        }
    }
}
