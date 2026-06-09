package io.embrace.android.embracesdk.fakes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedBackStack

/**
 * An Activity that uses Nav3 and calls [rememberObservedBackStack] to create and track a back stack that uses typed objects as
 * elements. Hosts the back stack inside a real [NavDisplay] consuming [NavEntry] instances.
 */
class TypedBackStackNav3Activity : ComponentActivity(), HasBackStack {

    @Volatile
    private var capturedBackStack: SnapshotStateList<Any>? = null

    @Suppress("UNCHECKED_CAST")
    override fun getBackStack(): SnapshotStateList<Any> = checkNotNull(capturedBackStack) { "Back stack not yet created" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val backStack = rememberObservedBackStack<Screen>(Screen.Home)
            @Suppress("UNCHECKED_CAST")
            capturedBackStack = backStack as SnapshotStateList<Any>
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = { screen -> NavEntry(key = screen) { } },
            )
        }
    }

    sealed class Screen {
        object Home : Screen() { override fun toString(): String = "home" }
        data class Detail(val id: Int) : Screen()
    }
}
