package io.embrace.android.embracesdk.fakes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedNavController

class ComposeNavHostActivity : ComponentActivity(), HasNavController {

    @Volatile
    private var capturedNavController: NavHostController? = null

    override fun getNavController(): NavController = checkNotNull(capturedNavController) { "NavController not yet created" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberObservedNavController()
            capturedNavController = navController
            NavHost(navController = navController, startDestination = "home") {
                composable("home") { }
                composable("about") { }
                composable("contacts") { }
            }
        }
    }
}
