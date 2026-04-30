package io.embrace.android.embracesdk.instrumentation.androidx.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.Navigator
import androidx.navigation.compose.rememberNavController
import io.embrace.android.embracesdk.instrumentation.androidx.navigation.internal.attachBackStack
import io.embrace.android.embracesdk.instrumentation.androidx.navigation.internal.onBackStackDestinationChange
import io.embrace.android.embracesdk.instrumentation.androidx.navigation.internal.trackNavigation
import io.embrace.android.embracesdk.internal.arch.navigation.findActivity

/**
 * Wrapper for [rememberNavController] that registers the returned [NavHostController] with the Embrace SDK so navigations are observed.
 *
 * Replace uses of [rememberNavController] with this Composable to enable the functionality.
 */
@Composable
public fun rememberObservedNavController(
    vararg navigators: Navigator<out NavDestination>,
): NavHostController {
    val navController = rememberNavController(*navigators)
    val context = LocalContext.current
    DisposableEffect(navController) {
        context.findActivity()?.let { activity ->
            trackNavigation(activity, navController)
        }
        onDispose { }
    }
    return navController
}

/**
 * Composable that creates a Nav3 back stack whose mutations are tracked as navigation state changes. The new navigation state value
 * will be what the [toString] method on the object returns.
 */
@Composable
public fun <T : Any> rememberObservedBackStack(vararg keys: T): SnapshotStateList<T> {
    val backStack = remember { mutableStateListOf(*keys) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(backStack, activity) {
        if (activity == null) {
            return@DisposableEffect onDispose { }
        }
        attachBackStack(activity)

        var lastReported: T? = null

        fun reportTopIfChanged() {
            val top = backStack.lastOrNull()
            if (top != null && top != lastReported) {
                onBackStackDestinationChange(activity, top.toString())
                lastReported = top
            }
        }

        reportTopIfChanged()
        val handle = Snapshot.Companion.registerApplyObserver { _, _ -> reportTopIfChanged() }
        onDispose { handle.dispose() }
    }
    return backStack
}
