package io.embrace.android.exampleapp.paradigms.social.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.paradigms.social.ui.PostDetailUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class PostDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val sampleData = requireContext().appGraph().sampleData
        val postId = arguments?.getString(SocialFragmentsActivity.ARG_POST_ID)
        val post = postId?.let(sampleData::post)
        if (post == null) {
            findNavController().popBackStack()
            return ComposeView(requireContext())
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ExampleAppTheme {
                    PostDetailUi(
                        post = post,
                        onAuthorClick = { handle ->
                            findNavController().navigate("profile/$handle")
                        },
                        onBack = { findNavController().popBackStack() },
                    )
                }
            }
        }
    }
}
