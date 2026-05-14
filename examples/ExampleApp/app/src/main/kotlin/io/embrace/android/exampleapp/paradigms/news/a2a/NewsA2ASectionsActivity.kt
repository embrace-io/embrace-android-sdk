package io.embrace.android.exampleapp.paradigms.news.a2a

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.TaskStackBuilder
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.paradigms.news.ui.NewsSectionsUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class NewsA2ASectionsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleAppTheme {
                val sampleData = appGraph().sampleData
                NewsSectionsUi(
                    title = "Sections (A2A)",
                    sections = sampleData.newsSections,
                    onSectionClick = { id ->
                        startActivity(NewsA2AArticleListActivity.newIntent(this, id))
                    },
                    onDeeplinkRandom = {
                        val article = sampleData.articles.random()
                        TaskStackBuilder.create(this)
                            .addNextIntent(newIntent(this))
                            .addNextIntent(NewsA2AArticleListActivity.newIntent(this, article.sectionId))
                            .addNextIntent(NewsA2AArticleDetailActivity.newIntent(this, article.id))
                            .startActivities()
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, NewsA2ASectionsActivity::class.java)
    }
}
