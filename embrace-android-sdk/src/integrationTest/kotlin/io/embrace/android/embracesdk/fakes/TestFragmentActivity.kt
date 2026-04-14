package io.embrace.android.embracesdk.fakes

import android.R
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.createGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment

class TestFragmentActivity : HasNavController, FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val navHostFragment = NavHostFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.content, navHostFragment)
            .commitNow()
        navHostFragment.navController.graph = navHostFragment.navController.createGraph(startDestination = "home") {
            fragment<Fragment>("home")
            fragment<Fragment>("about")
            fragment<Fragment>("contacts")
        }
    }

    override fun getNavController(): NavController {
        val navHostFragment = supportFragmentManager.fragments
            .first { it is NavHostFragment } as NavHostFragment
        return navHostFragment.navController
    }
}
