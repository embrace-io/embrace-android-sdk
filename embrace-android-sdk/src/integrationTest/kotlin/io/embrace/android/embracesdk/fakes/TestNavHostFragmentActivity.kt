package io.embrace.android.embracesdk.fakes

import android.R
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment

/**
 * An abstract Activity that contains a [NavHostFragment] whose [NavController] is made available via the [HasNavController] interface.
 *
 * Activities extend this will have its [NavController] automatically detected and registered by the Navigation State
 * instrumentation if the Compose Navigation module is included by an app.
 */
abstract class TestNavHostFragmentActivity : HasNavController, FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val navHostFragment = NavHostFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.content, navHostFragment)
            .commitNow()
        navHostFragment.navController.graph = createNavGraph(navHostFragment.navController)
    }
    override fun getNavController(): NavController {
        val navHostFragment = supportFragmentManager.fragments.first { it is NavHostFragment } as NavHostFragment
        return navHostFragment.navController
    }

    abstract fun createNavGraph(navController: NavController): NavGraph
}
