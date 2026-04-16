package io.embrace.android.embracesdk.fakes

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.createGraph
import androidx.navigation.fragment.dialog
import androidx.navigation.fragment.fragment

/**
 * Activity with a Dialog destination
 */
class DialogNavHostFragmentActivity : TestNavHostFragmentActivity() {
    override fun createNavGraph(navController: NavController): NavGraph =
        navController.createGraph(startDestination = "home") {
            fragment<Fragment>("home")
            dialog<DialogFragment>("confirm_dialog")
        }
}
