package io.embrace.android.exampleapp.paradigms.news.a2a

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.news.ui.NewsArticleListUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class NewsA2AArticleListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sectionId = intent.getStringExtra(EXTRA_SECTION_ID)
        val section = sectionId?.let(SampleData::section)
        if (section == null) {
            Toast.makeText(this, "Unknown section", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val articles = SampleData.articlesIn(section.id)
        setContent {
            ExampleAppTheme {
                NewsArticleListUi(
                    sectionTitle = section.title,
                    articles = articles,
                    onArticleClick = { id ->
                        startActivity(NewsA2AArticleDetailActivity.newIntent(this, id))
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_SECTION_ID = "section_id"

        fun newIntent(context: Context, sectionId: String): Intent =
            Intent(context, NewsA2AArticleListActivity::class.java)
                .putExtra(EXTRA_SECTION_ID, sectionId)
    }
}
