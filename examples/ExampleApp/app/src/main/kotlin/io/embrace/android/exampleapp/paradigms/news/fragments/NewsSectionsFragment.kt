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
import io.embrace.android.exampleapp.paradigms.news.ui.NewsSectionsUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class NewsSectionsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            ExampleAppTheme {
                val sampleData = appGraph().sampleData
                NewsSectionsUi(
                    title = "Sections (Fragments)",
                    sections = sampleData.newsSections,
                    onSectionClick = { id ->
                        findNavController().navigate("section/$id")
                    },
                    onDeeplinkRandom = {
                        val article = sampleData.articles.random()
                        val navController = findNavController()
                        navController.navigate("section/${article.sectionId}")
                        navController.navigate("article/${article.id}")
                    },
                )
            }
        }
    }
}
