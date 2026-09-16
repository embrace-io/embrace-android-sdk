package io.embrace.android.exampleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.exampleapp.ui.CodeExample
import io.embrace.android.exampleapp.ui.CodeExampleDetailScreen
import io.embrace.android.exampleapp.ui.CodeExampleListScreen
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val examples = CodeExample.entries
        enableEdgeToEdge()
        setContent {
            ExampleAppTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        CodeExampleListScreen(navController, examples)
                    }
                    examples.forEach { example ->
                        composable(example.route) {
                            CodeExampleDetailScreen(navController, example)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // after a delay, end some of the experiment state declared at startup from a background thread
        thread(name = "experiment-updater") {
            Thread.sleep(5_000L)
            Embrace.untrackExperiment(id = "common")
            Embrace.untrackFeatureFlag(id = "ff-2")
            Embrace.logInfo(message = "experiments updated")
        }
    }
}
