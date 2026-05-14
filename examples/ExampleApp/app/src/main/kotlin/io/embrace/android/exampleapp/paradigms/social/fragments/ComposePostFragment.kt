package io.embrace.android.exampleapp.paradigms.social.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import io.embrace.android.exampleapp.paradigms.social.ui.ComposePostUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class ComposePostFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ExampleAppTheme {
                ComposePostUi(
                    onPost = { body ->
                        val result = Bundle().apply {
                            putString(SocialFragmentsActivity.FRAGMENT_RESULT_KEY_BODY, body)
                        }
                        setFragmentResult(SocialFragmentsActivity.FRAGMENT_RESULT_COMPOSE, result)
                        findNavController().popBackStack()
                    },
                    onCancel = { findNavController().popBackStack() },
                )
            }
        }
    }
}
