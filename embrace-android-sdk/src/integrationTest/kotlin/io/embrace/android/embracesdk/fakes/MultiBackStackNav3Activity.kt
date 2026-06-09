package io.embrace.android.embracesdk.fakes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedBackStack

/**
 * An Activity that uses Nav3 and has multiple back stacks that are created and tracked using [rememberObservedBackStack].
 * Each back stack has its own [NavDisplay] and both are alive in the composition simultaneously.
 */
class MultiBackStackNav3Activity : ComponentActivity() {

    @Volatile
    private var backStackPrimary: SnapshotStateList<Any>? = null

    @Volatile
    private var backStackSecondary: SnapshotStateList<Any>? = null

    fun getPrimaryBackStack(): SnapshotStateList<Any> = checkNotNull(backStackPrimary) { "Primary back stack not yet created" }

    fun getSecondaryBackStack(): SnapshotStateList<Any> = checkNotNull(backStackSecondary) { "Secondary back stack not yet created" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val primary = rememberObservedBackStack<Any>("primary-home")
            val secondary = rememberObservedBackStack<Any>("secondary-home")
            backStackPrimary = primary
            backStackSecondary = secondary
            NavDisplay(
                backStack = primary,
                onBack = { primary.removeLastOrNull() },
                entryProvider = { key -> NavEntry(key = key) { } },
            )
            NavDisplay(
                backStack = secondary,
                onBack = { secondary.removeLastOrNull() },
                entryProvider = { key -> NavEntry(key = key) { } },
            )
        }
    }
}
