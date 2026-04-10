package io.embrace.android.embracesdk.fakes

import android.app.Activity
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphNavigator
import androidx.navigation.testing.TestNavHostController

/**
 * An [Activity] that creates a custom [NavController] on create and exposes it.
 */
class TestNavControllerActivity : Activity() {
    private lateinit var navController: TestNavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navController = TestNavHostController(this).apply {
            val graphNavigator = navigatorProvider.getNavigator(NavGraphNavigator::class.java)
            graph = graphNavigator.createDestination().apply {
                addDestination(NavDestination("test").apply { route = "home" })
                addDestination(NavDestination("test").apply { route = "about" })
                addDestination(NavDestination("test").apply { route = "contacts" })
                setStartDestination("home")
            }
        }
    }

    fun getNavController(): NavController = navController
}
