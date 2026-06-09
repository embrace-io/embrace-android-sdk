package io.embrace.android.embracesdk.fakes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedBackStack

/**
 * An Activity that uses Nav3 but doesn't use [rememberObservedBackStack] to create and track the back stack.
 */
class Nav3UnobservedActivity : ComponentActivity(), HasBackStack {

    @Volatile
    private var capturedBackStack: SnapshotStateList<Any>? = null

    override fun getBackStack(): SnapshotStateList<Any> = checkNotNull(capturedBackStack) { "Back stack not yet created" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val backStack = remember { mutableStateListOf<Any>("home") }
            capturedBackStack = backStack
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = { key -> NavEntry(key = key) { } },
            )
        }
    }
}
