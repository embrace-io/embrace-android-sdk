package io.embrace.android.exampleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class ObservedBackStackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleAppTheme {
//                val backStack = rememberObservedBackStack<Screen>(Screen.Home)
                val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = { screen -> entryFor(screen, backStack) },
                )
            }
        }
    }

    sealed class Screen: NavKey {
        object Home : Screen() {
            override fun toString(): String = "back-stack-home"
        }

        object About : Screen() {
            override fun toString(): String = "back-stack-about"
        }

        object Contacts : Screen() {
            override fun toString(): String = "back-stack-contacts"
        }
    }

    private fun entryFor(screen: Screen, backStack: SnapshotStateList<Screen>): NavEntry<Screen> =
        NavEntry(key = screen) {
            ScreenContent(title = screen.toString(), backStack = backStack)
        }
}

@Composable
private fun ScreenContent(title: String, backStack: SnapshotStateList<ObservedBackStackActivity.Screen>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title.replaceFirstChar { it.titlecase() },
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(24.dp))
        Text("Push or pop entries — the SDK records each top-of-stack change as a navigation state.")
        Spacer(Modifier.height(16.dp))
        listOf(
            ObservedBackStackActivity.Screen.Home,
            ObservedBackStackActivity.Screen.About,
            ObservedBackStackActivity.Screen.Contacts
        ).filter {
            it.toString() != title
        }.forEach { screen ->
            Button(onClick = { backStack.add(screen) }) {
                Text("Push ${screen.toString().replaceFirstChar { it.titlecase() }}")
            }
            Spacer(Modifier.height(8.dp))
        }
        if (backStack.size > 1) {
            Button(onClick = { backStack.removeLastOrNull() }) {
                Text("Pop")
            }
        }
    }
}
