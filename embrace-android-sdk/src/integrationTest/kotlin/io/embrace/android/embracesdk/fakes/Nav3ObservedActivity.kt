package io.embrace.android.embracesdk.fakes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedBackStack

/**
 * An Activity that uses Nav3 and calls [rememberObservedBackStack] to create and track the back stack.
 * Hosts the back stack inside a [NavDisplay] and consumes [NavEntry] instances.
 */
class Nav3ObservedActivity : ComponentActivity(), HasBackStack {

    @Volatile
    private var capturedBackStack: SnapshotStateList<Any>? = null

    @Suppress("UNCHECKED_CAST")
    override fun getBackStack(): SnapshotStateList<Any> = checkNotNull(capturedBackStack) { "Back stack not yet created" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val backStack = rememberObservedBackStack<Any>("home")
            capturedBackStack = backStack
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = { key -> NavEntry(key = key) { } },
            )
        }
    }
}
