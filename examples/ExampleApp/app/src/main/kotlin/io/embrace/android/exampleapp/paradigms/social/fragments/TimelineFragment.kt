package io.embrace.android.exampleapp.paradigms.social.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.social.ui.TimelineUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class TimelineFragment : Fragment() {

    private var postedBody by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFragmentResultListener(SocialFragmentsActivity.FRAGMENT_RESULT_COMPOSE) { _, bundle ->
            postedBody = bundle.getString(SocialFragmentsActivity.FRAGMENT_RESULT_KEY_BODY)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ExampleAppTheme {
                TimelineUi(
                    title = "Home (Fragments)",
                    staticPosts = SampleData.posts,
                    onPostClick = { id ->
                        findNavController().navigate("post/$id")
                    },
                    onAuthorClick = { handle ->
                        findNavController().navigate("profile/$handle")
                    },
                    onCompose = {
                        findNavController().navigate(SocialFragmentsActivity.ROUTE_COMPOSE)
                    },
                    postedBody = postedBody,
                )
            }
        }
    }
}
