package io.embrace.android.embracesdk.fakes

import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment

/**
 * Activity that contains destinations that have parameterized routes
 */
class ArgTemplateNavHostFragmentActivity : TestNavHostFragmentActivity() {
    override fun createNavGraph(navController: NavController): NavGraph =
        navController.createGraph(startDestination = "home") {
            fragment<Fragment>("home")
            fragment<Fragment>("profile/{userId}")
            fragment<Fragment>("order/{orderId}/details")
        }
}
