package io.embrace.android.embracesdk.fakes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.NavController
import androidx.navigation.createGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment

/**
 * Base class for activities whose [NavController] is hosted inside a node in a View hierarchy such that the auto discovery
 * of NavControllers does not pick it up.
 */
abstract class NonAutoDetectedNavHostActivity : HasNavController, FragmentActivity() {
    val navHostContainerId: Int = View.generateViewId()
    private val fragmentContainerID: Int = View.generateViewId()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FragmentContainerView(this).apply { id = fragmentContainerID })
        if (supportFragmentManager.findFragmentById(fragmentContainerID) == null) {
            supportFragmentManager.beginTransaction()
                .add(fragmentContainerID, NavControllerFragment(navHostContainerId))
                .commitNow()
        }
    }

    class NavControllerFragment(val navControllerContainerId: Int) : Fragment() {
        val navController: NavController
            get() {
                val navHost = childFragmentManager.findFragmentById(navControllerContainerId) as NavHostFragment
                return navHost.navController
            }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FragmentContainerView(requireContext()).apply { id = navControllerContainerId }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            if (childFragmentManager.findFragmentById(navControllerContainerId) == null) {
                val navHost = NavHostFragment()
                childFragmentManager.beginTransaction()
                    .add(navControllerContainerId, navHost)
                    .commitNow()
                navHost.navController.graph = navHost.navController.createGraph(startDestination = "home") {
                    fragment<Fragment>("home")
                    fragment<Fragment>("about")
                    fragment<Fragment>("contacts")
                }
            }
        }
    }
}
