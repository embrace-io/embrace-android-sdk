package io.embrace.android.exampleapp.paradigms.social.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.embrace.android.exampleapp.paradigms.social.ui.ProfileScreen
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val handle = arguments?.getString(SocialFragmentsActivity.ARG_HANDLE)
        if (handle.isNullOrEmpty()) {
            findNavController().popBackStack()
            return ComposeView(requireContext())
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ExampleAppTheme {
                    ProfileScreen(
                        handle = handle,
                        onPostClick = { id -> findNavController().navigate("post/$id") },
                        onBack = { findNavController().popBackStack() },
                    )
                }
            }
        }
    }
}
