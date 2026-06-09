package io.embrace.android.embracesdk.fakes

import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedBackStack

/**
 * Variant of [Nav3ObservedActivity] where the back stack is created in a different context.
 */
class WrappedContextNav3ComposeActivity : ComponentActivity(), HasBackStack {

    @Volatile
    private var capturedBackStack: SnapshotStateList<Any>? = null

    override fun getBackStack(): SnapshotStateList<Any> = checkNotNull(capturedBackStack) { "Back stack not yet created" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val wrapped = remember(this) { ContextWrapper(this) }
            CompositionLocalProvider(LocalContext provides wrapped) {
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
}
