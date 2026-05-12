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
import io.embrace.android.exampleapp.paradigms.news.ui.NewsArticleDetailUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class NewsArticleDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val sampleData = requireContext().appGraph().sampleData
        val articleId = arguments?.getString(NewsFragmentsActivity.ARG_ARTICLE_ID)
        val article = articleId?.let(sampleData::article)
        if (article == null) {
            findNavController().popBackStack()
            return ComposeView(requireContext())
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ExampleAppTheme {
                    NewsArticleDetailUi(
                        article = article,
                        onBack = { findNavController().popBackStack() },
                    )
                }
            }
        }
    }
}
