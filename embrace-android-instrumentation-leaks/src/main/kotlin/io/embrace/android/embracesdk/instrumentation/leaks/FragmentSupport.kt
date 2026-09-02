package io.embrace.android.embracesdk.instrumentation.leaks

import android.app.Activity
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot

/**
 * Attaches Fragment and Fragment View leak tracking to an Activity, if the host app includes the Fragment library.
 * `androidx.fragment:fragment` is a `compileOnly` dependency of this module - an app that doesn't include it must never need
 * to resolve it, so [FragmentLeakDetectionLifecycleCallbacks], the only implementation of this interface that touches Fragment
 * types, must never be referenced by a statically-typed field or property outside this file. Everywhere else in this module
 * only ever holds a reference of this interface's type, which is always resolvable.
 */
internal interface FragmentSupport {
    fun onActivityCreated(activity: Activity)
}

/**
 * Used in place of [FragmentLeakDetectionLifecycleCallbacks] when the Fragment library isn't present on the host app's
 * classpath, or when Fragment leak detection isn't enabled.
 */
internal object NoOpFragmentSupport : FragmentSupport {
    override fun onActivityCreated(activity: Activity) = Unit
}

/**
 * Attempts to build a [FragmentSupport] backed by the real Fragment library.
 *
 * Constructing [FragmentLeakDetectionLifecycleCallbacks] requires resolving its superclass,
 * `FragmentManager.FragmentLifecycleCallbacks` - if the Fragment library isn't present on the host app's classpath, that
 * throws [NoClassDefFoundError], which is expected for any app that doesn't use Fragments and is not logged as a failure.
 * [NoOpFragmentSupport] is used instead. This check needs no [Activity] and runs once, for the life of the process - the
 * per-Activity `is FragmentActivity` check still happens on every Activity, inside [FragmentLeakDetectionLifecycleCallbacks]
 * itself, since some Activities in the same app may not extend it even when the library is present.
 */
internal fun createFragmentSupport(
    leakDetector: LeakDetector,
    activeSessionIdsProvider: () -> SessionIdsSnapshot,
): FragmentSupport {
    return runCatching { FragmentLeakDetectionLifecycleCallbacks(leakDetector, activeSessionIdsProvider) }
        .getOrDefault(NoOpFragmentSupport)
}
