package io.embrace.android.embracesdk.fakes

import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedNavController

class WrappedContextComposeNavHostActivity : ComponentActivity(), HasNavController {

    @Volatile
    private var capturedNavController: NavHostController? = null

    override fun getNavController(): NavController = checkNotNull(capturedNavController) { "NavController not yet created" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val wrapped = remember(this) { ContextWrapper(this) }
            CompositionLocalProvider(LocalContext provides wrapped) {
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
}
