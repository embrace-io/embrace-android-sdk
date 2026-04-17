package io.embrace.android.embracesdk.fakes

import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment

/**
 * Basic Activity with Fragment destinations
 */
class BasicNavHostFragmentActivity : TestNavHostFragmentActivity() {
    override fun createNavGraph(navController: NavController): NavGraph {
        return navController.createGraph(startDestination = "home") {
            fragment<Fragment>("home")
            fragment<Fragment>("about")
            fragment<Fragment>("contacts")
        }
    }
}
