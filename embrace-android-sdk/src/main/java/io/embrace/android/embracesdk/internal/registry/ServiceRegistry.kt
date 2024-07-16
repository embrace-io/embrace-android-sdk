package io.embrace.android.embracesdk.internal.registry

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import io.embrace.android.embracesdk.internal.session.MemoryCleanerService
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.session.lifecycle.StartupListener
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An object that holds all of the services that are registered with the SDK. This makes it simpler
 * to remember to set callbacks & close resources when creating a new service.
 */
internal class ServiceRegistry(
    private val logger: EmbLogger
) : Closeable {

    private val registry = mutableListOf<Any>()
    private var initialized = AtomicBoolean(false)

    // lazy init avoids type checks at startup until absolutely necessary.
    // once these variables are initialized, no further services should be registered.
    val closeables by lazy { registry.filterIsInstance<Closeable>() }
    val memoryCleanerListeners by lazy { registry.filterIsInstance<MemoryCleanerListener>() }
    val processStateListeners by lazy { registry.filterIsInstance<ProcessStateListener>() }
    val activityLifecycleListeners by lazy { registry.filterIsInstance<ActivityLifecycleListener>() }
    val startupListener by lazy { registry.filterIsInstance<StartupListener>() }

    fun registerServices(vararg services: Any?) {
        services.forEach(::registerService)
    }

    fun registerService(service: Any?) {
        if (initialized.get()) {
            error("Cannot register a service - already initialized.")
        }
        if (service == null) {
            return
        }
        registry.add(service)
    }

    fun closeRegistration() {
        initialized.set(true)
    }

    fun registerActivityListeners(processStateService: ProcessStateService) = processStateListeners.forEachSafe(
        "Failed to register activity listener",
        processStateService::addListener
    )

    fun registerActivityLifecycleListeners(activityLifecycleTracker: ActivityTracker) = activityLifecycleListeners.forEachSafe(
        "Failed to register activity lifecycle listener",
        activityLifecycleTracker::addListener
    )

    fun registerMemoryCleanerListeners(memoryCleanerService: MemoryCleanerService) =
        memoryCleanerListeners.forEachSafe(
            "Failed to register memory cleaner listener",
            memoryCleanerService::addListener
        )

    fun registerStartupListener(activityLifecycleTracker: ActivityTracker) =
        startupListener.forEachSafe(
            "Failed to register application lifecycle listener",
            activityLifecycleTracker::addStartupListener
        )

    // close all of the services in one go. this prevents someone creating a Closeable service
    // but forgetting to close it.
    override fun close() = closeables.forEachSafe("Failed to close service", Closeable::close)

    private fun <T> List<T>.forEachSafe(msg: String, action: (t: T) -> Unit) {
        this.forEach {
            try {
                action(it)
            } catch (exc: Throwable) {
                logger.logError(msg, exc)
            }
        }
    }
}
