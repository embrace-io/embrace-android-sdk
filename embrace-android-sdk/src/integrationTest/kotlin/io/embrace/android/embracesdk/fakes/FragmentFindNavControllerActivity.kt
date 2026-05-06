package io.embrace.android.embracesdk.fakes

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController

class FragmentFindNavControllerActivity : NonAutoDetectedNavHostActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = RegisterNavObserverFragmentFactory(fragmentResumeCallback)
        super.onCreate(savedInstanceState)
    }

    override fun getNavController(): NavController = findNavController(navHostContainerId)

    @Volatile
    var fragmentResumeCallback: (Activity, NavController) -> Unit = { _, _ -> }

    class ObservedFragment(
        private val observeNavigation: (Activity, NavController) -> Unit,
    ) : Fragment() {
        override fun onResume() {
            super.onResume()
            observeNavigation(requireActivity(), findNavController())
        }
    }

    private class RegisterNavObserverFragmentFactory(
        private val onFragmentInstantiation: (Activity, NavController) -> Unit,
    ) : FragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment =
            ObservedFragment(onFragmentInstantiation)
    }
}
