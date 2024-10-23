package io.embrace.android.embracesdk.internal.capture.memory

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.EmbLogger

/**
 * Captures custom breadcrumbs.
 */
internal class MemoryWarningDataSource(
    private val application: Application,
    private val clock: Clock,
    sessionSpanWriter: SessionSpanWriter,
    private val logger: EmbLogger
) : ComponentCallbacks2, DataSourceImpl<SessionSpanWriter>(
    destination = sessionSpanWriter,
    logger = logger,
    limitStrategy = UpToLimitStrategy { 10 }
) {

    override fun enableDataCapture() {
        application.applicationContext.registerComponentCallbacks(this)
    }

    override fun disableDataCapture() {
        try {
            application.applicationContext.unregisterComponentCallbacks(this)
        } catch (ex: Exception) {
            logger.logWarning(
                "Error when closing MemoryWarningDataSource",
                ex
            )
        }
    }

    private fun onMemoryWarning(timestamp: Long) {
        captureData(
            inputValidation = NoInputValidation,
            captureAction = {
                addEvent(SchemaType.MemoryWarning(), timestamp)
            }
        )
    }

    /**
     * Called when the OS has determined that it is a good time for a process to trim unneeded
     * memory.
     *
     * @param trimLevel the context of the trim, giving a hint of the amount of trimming.
     */
    override fun onTrimMemory(trimLevel: Int) {
        @Suppress("DEPRECATION")
        if (trimLevel == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            try {
                onMemoryWarning(clock.now())
            } catch (ex: Exception) {
                logger.logWarning(
                    "Failed to handle onTrimMemory (low memory) event",
                    ex
                )
            }
        }
    }

    override fun onConfigurationChanged(configuration: Configuration) {}
    override fun onLowMemory() {}
}
