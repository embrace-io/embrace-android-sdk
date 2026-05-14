package io.embrace.android.exampleapp.paradigms.news.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.paradigms.news.ui.NewsArticleListUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class NewsArticleListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val sampleData = requireContext().appGraph().sampleData
        val sectionId = arguments?.getString(NewsFragmentsActivity.ARG_SECTION_ID)
        val section = sectionId?.let(sampleData::section)
        if (section == null) {
            findNavController().popBackStack()
            return ComposeView(requireContext())
        }
        val articles = sampleData.articlesIn(section.id)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ExampleAppTheme {
                    NewsArticleListUi(
                        sectionTitle = section.title,
                        articles = articles,
                        onArticleClick = { id ->
                            findNavController().navigate("article/$id")
                        },
                        onBack = { findNavController().popBackStack() },
                    )
                }
            }
        }
    }
}
