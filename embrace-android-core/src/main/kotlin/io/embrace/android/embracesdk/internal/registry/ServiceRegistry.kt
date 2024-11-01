package io.embrace.android.embracesdk.internal.registry

import io.embrace.android.embracesdk.internal.Systrace
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
class ServiceRegistry : Closeable {

    private val registry = mutableListOf<Lazy<Any?>>()
    private val finalRegistry: List<Any?> by lazy { registry.map(Lazy<Any?>::value) }
    private var initialized = AtomicBoolean(false)

    // lazy init avoids type checks at startup until absolutely necessary.
    // once these variables are initialized, no further services should be registered.
    val closeables: List<Closeable> by lazy { finalRegistry.filterIsInstance<Closeable>() }
    val memoryCleanerListeners: List<MemoryCleanerListener> by lazy {
        finalRegistry.filterIsInstance<MemoryCleanerListener>()
    }
    val processStateListeners: List<ProcessStateListener> by lazy {
        finalRegistry.filterIsInstance<ProcessStateListener>()
    }
    val activityLifecycleListeners: List<ActivityLifecycleListener> by lazy {
        finalRegistry.filterIsInstance<ActivityLifecycleListener>()
    }
    val startupListener: List<StartupListener> by lazy { finalRegistry.filterIsInstance<StartupListener>() }

    fun registerServices(vararg services: Lazy<Any?>) {
        Systrace.trace("register-services") {
            services.forEach(::registerService)
        }
    }

    fun registerService(service: Lazy<Any?>) {
        if (initialized.get()) {
            error("Cannot register a service - already initialized.")
        }
        registry.add(service)
    }

    fun closeRegistration() {
        initialized.set(true)
    }

    fun registerActivityListeners(processStateService: ProcessStateService): Unit =
        processStateListeners.forEachSafe(
            processStateService::addListener
        )

    fun registerActivityLifecycleListeners(activityLifecycleTracker: ActivityTracker): Unit =
        activityLifecycleListeners.forEachSafe(
            activityLifecycleTracker::addListener
        )

    fun registerMemoryCleanerListeners(memoryCleanerService: MemoryCleanerService): Unit =
        memoryCleanerListeners.forEachSafe(
            memoryCleanerService::addListener
        )

    fun registerStartupListener(activityLifecycleTracker: ActivityTracker): Unit =
        startupListener.forEachSafe(
            activityLifecycleTracker::addStartupListener
        )

    // close all of the services in one go. this prevents someone creating a Closeable service
    // but forgetting to close it.
    override fun close(): Unit = closeables.forEachSafe(Closeable::close)

    private fun <T> List<T>.forEachSafe(action: (t: T) -> Unit) {
        this.forEach {
            runCatching {
                action(it)
            }
        }
    }
}
