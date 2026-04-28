package io.embrace.android.exampleapp

import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment

class MainFragmentActivity : NavFragmentActivity() {

    override fun createNavGraph(navController: NavController): NavGraph =
        navController.createGraph(startDestination = DESTINATIONS.first()) {
            DESTINATIONS.forEach { dest ->
                fragment<PlaceholderFragment>(dest).apply { label = dest }
            }
        }

    override fun createActions(navController: NavController): List<Pair<String, () -> Unit>> =
        DESTINATIONS.map { dest ->
            dest.replaceFirstChar { it.uppercase() } to {
                if (navController.currentDestination?.route != dest) {
                    navController.navigate(dest)
                }
            }
        }

    private companion object {
        val DESTINATIONS = listOf("Home", "About", "Settings", "Profile")
    }
}

