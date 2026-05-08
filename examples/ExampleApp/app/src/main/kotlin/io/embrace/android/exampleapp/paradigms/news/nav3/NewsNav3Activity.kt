package io.embrace.android.exampleapp.paradigms.news.nav3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.news.ui.NewsArticleDetailUi
import io.embrace.android.exampleapp.paradigms.news.ui.NewsArticleListUi
import io.embrace.android.exampleapp.paradigms.news.ui.NewsSectionsUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class NewsNav3Activity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleAppTheme {
                val backStack = remember { mutableStateListOf<NewsNav3Key>(NewsNav3Key.Sections) }
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = { key ->
                        when (key) {
                            is NewsNav3Key.Sections -> NavEntry(key) {
                                NewsSectionsUi(
                                    title = "Sections (Nav3)",
                                    sections = SampleData.newsSections,
                                    onSectionClick = { id ->
                                        backStack.add(NewsNav3Key.ArticleList(id))
                                    },
                                    onDeeplinkRandom = {
                                        val article = SampleData.articles.random()
                                        backStack.addAll(
                                            listOf(
                                                NewsNav3Key.ArticleList(article.sectionId),
                                                NewsNav3Key.ArticleDetail(article.id),
                                            ),
                                        )
                                    },
                                )
                            }
                            is NewsNav3Key.ArticleList -> NavEntry(key) {
                                val section = SampleData.section(key.sectionId)
                                if (section == null) {
                                    backStack.removeLastOrNull()
                                } else {
                                    NewsArticleListUi(
                                        sectionTitle = section.title,
                                        articles = SampleData.articlesIn(section.id),
                                        onArticleClick = { id ->
                                            backStack.add(NewsNav3Key.ArticleDetail(id))
                                        },
                                        onBack = { backStack.removeLastOrNull() },
                                    )
                                }
                            }
                            is NewsNav3Key.ArticleDetail -> NavEntry(key) {
                                val article = SampleData.article(key.articleId)
                                if (article == null) {
                                    backStack.removeLastOrNull()
                                } else {
                                    NewsArticleDetailUi(
                                        article = article,
                                        onBack = { backStack.removeLastOrNull() },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, NewsNav3Activity::class.java)
    }
}
