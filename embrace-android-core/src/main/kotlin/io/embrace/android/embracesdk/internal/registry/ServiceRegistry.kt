package io.embrace.android.embracesdk.internal.registry

import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.state.AppStateListener
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.session.id.SessionTracker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import java.io.Closeable

/**
 * An object that holds all of the services that are registered with the SDK. This makes it simpler
 * to remember to set callbacks & close resources when creating a new service.
 */
class ServiceRegistry : Closeable {

    private val registry = mutableListOf<Lazy<Any?>>()
    private val finalRegistry: List<Any?> by lazy { registry.map(Lazy<Any?>::value) }

    // lazy init avoids type checks at startup until absolutely necessary.
    // once these variables are initialized, no further services should be registered.
    val closeables: List<Closeable> by lazy { finalRegistry.filterIsInstance<Closeable>() }
    val sessionChangeListeners: List<SessionChangeListener> by lazy {
        finalRegistry.filterIsInstance<SessionChangeListener>()
    }
    val appStateListeners: List<AppStateListener> by lazy {
        finalRegistry.filterIsInstance<AppStateListener>()
    }

    fun registerServices(vararg services: Lazy<Any?>) {
        EmbTrace.traceAsync("register-services") {
            services.forEach(::registerService)
        }
    }

    fun registerService(service: Lazy<Any?>) {
        registry.add(service)
    }

    fun registerAppStateListeners(appStateTracker: AppStateTracker): Unit =
        appStateListeners.forEachSafe(
            appStateTracker::addListener
        )

    fun registerSessionChangeListeners(sessionTracker: SessionTracker): Unit =
        sessionChangeListeners.forEachSafe(
            sessionTracker::addListener
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
