package io.embrace.android.exampleapp.paradigms.news.navcompose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedNavController
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.paradigms.news.ui.NewsArticleDetailUi
import io.embrace.android.exampleapp.paradigms.news.ui.NewsArticleListUi
import io.embrace.android.exampleapp.paradigms.news.ui.NewsSectionsUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class NewsNavComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleAppTheme {
                val sampleData = appGraph().sampleData
                val navController = rememberObservedNavController()
                NavHost(
                    navController = navController,
                    startDestination = NewsRoute.Sections,
                ) {
                    composable<NewsRoute.Sections> {
                        NewsSectionsUi(
                            title = "Sections (Nav-Compose)",
                            sections = sampleData.newsSections,
                            onSectionClick = { id ->
                                navController.navigate(NewsRoute.ArticleList(id))
                            },
                            onDeeplinkRandom = {
                                val article = sampleData.articles.random()
                                navController.navigate(NewsRoute.ArticleList(article.sectionId))
                                navController.navigate(NewsRoute.ArticleDetail(article.id))
                            },
                        )
                    }
                    composable<NewsRoute.ArticleList> { entry ->
                        val route: NewsRoute.ArticleList = entry.toRoute()
                        val section = sampleData.section(route.sectionId)
                        if (section == null) {
                            navController.popBackStack()
                        } else {
                            NewsArticleListUi(
                                sectionTitle = section.title,
                                articles = sampleData.articlesIn(section.id),
                                onArticleClick = { id ->
                                    navController.navigate(NewsRoute.ArticleDetail(id))
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable<NewsRoute.ArticleDetail> { entry ->
                        val route: NewsRoute.ArticleDetail = entry.toRoute()
                        val article = sampleData.article(route.articleId)
                        if (article == null) {
                            navController.popBackStack()
                        } else {
                            NewsArticleDetailUi(
                                article = article,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, NewsNavComposeActivity::class.java)
    }
}
