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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedNavController
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class ObservedNavControllerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleAppTheme {
                val navController = rememberObservedNavController()
                NavHost(navController = navController, startDestination = "nav-controller-home") {
                    composable("nav-controller-home") { ScreenContent("Home", navController) }
                    composable("nav-controller-about") { ScreenContent("About", navController) }
                    composable("nav-controller-contacts") { ScreenContent("Contacts", navController) }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(title: String, navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Text("Navigate to a different destination — the SDK records each transition as a state change.")
        Spacer(Modifier.height(16.dp))
        listOf("nav-controller-home", "nav-controller-about", "nav-controller-contacts")
            .filter {
                it != title.lowercase()
            }
            .forEach { route ->
                Button(onClick = { navController.navigate(route) }) {
                    Text("Go to ${route.replaceFirstChar { it.titlecase() }}")
                }
                Spacer(Modifier.height(8.dp))
            }
    }
}
