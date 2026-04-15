package io.embrace.android.embracesdk.fakes

import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment
import kotlinx.serialization.Serializable

/**
 * Activity with destinations where the routes are Serializable classes
 */
class FqcnRouteActivityNavHost : TestNavHostFragmentActivity() {
    override fun createNavGraph(navController: NavController): NavGraph =
        navController.createGraph(startDestination = FqcnHome::class) {
            fragment<Fragment, FqcnHome>()
            fragment<Fragment, FqcnDetail>()
        }

    @Serializable
    object FqcnHome

    @Serializable
    data class FqcnDetail(val itemId: Int)
}
