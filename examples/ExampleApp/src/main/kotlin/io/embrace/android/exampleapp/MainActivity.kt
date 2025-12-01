package io.embrace.android.exampleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.embrace.android.exampleapp.ui.CodeExample
import io.embrace.android.exampleapp.ui.CodeExampleDetailScreen
import io.embrace.android.exampleapp.ui.CodeExampleListScreen
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

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
}
