package io.embrace.android.embracesdk.fakes

import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment
import androidx.navigation.navigation

/**
 * Activity with a destination that is itself a [NavGraph]
 */
class NestedGraphNavHostFragmentActivity : TestNavHostFragmentActivity() {
    override fun createNavGraph(navController: NavController): NavGraph =
        navController.createGraph(startDestination = "home") {
            fragment<Fragment>("home")
            navigation(startDestination = "general", route = "settings_graph") {
                fragment<Fragment>("general")
                fragment<Fragment>("privacy")
            }
        }
}
