package io.embrace.android.embracesdk.fakes

import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Activity with destinations whose routes are serializable objects
 */
class SerializableRouteNavHostFragmentActivity : TestNavHostFragmentActivity() {
    override fun createNavGraph(navController: NavController): NavGraph =
        navController.createGraph(startDestination = TypeSafeHome::class) {
            fragment<Fragment, TypeSafeHome>()
            fragment<Fragment, TypeSafeProfile>()
        }

    @Serializable
    @SerialName("home")
    object TypeSafeHome

    @Serializable
    @SerialName("profile")
    data class TypeSafeProfile(val userId: String)
}
