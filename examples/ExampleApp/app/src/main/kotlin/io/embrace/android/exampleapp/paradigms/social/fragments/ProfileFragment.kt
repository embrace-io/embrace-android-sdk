package io.embrace.android.exampleapp.paradigms.social.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.social.ui.ProfileUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val handle = arguments?.getString(SocialFragmentsActivity.ARG_HANDLE)
        val author = handle?.let(SampleData::author)
        if (author == null) {
            findNavController().popBackStack()
            return ComposeView(requireContext())
        }
        val authorPosts = SampleData.posts.filter { it.authorHandle == author.handle }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ExampleAppTheme {
                    ProfileUi(
                        author = author,
                        authorPosts = authorPosts,
                        onPostClick = { id ->
                            findNavController().navigate("post/$id")
                        },
                        onBack = { findNavController().popBackStack() },
                    )
                }
            }
        }
    }
}
