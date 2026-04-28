package io.embrace.android.embracesdk.internal.instrumentation.compose.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.Navigator
import androidx.navigation.compose.rememberNavController

/**
 * Wrapper for [androidx.navigation.compose.rememberNavController] that registers the returned [NavHostController] with the
 * Embrace SDK so navigations are observed.
 *
 * Simply replace uses of [rememberNavController] with this Composable to get this functionality.
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

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}
