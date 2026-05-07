package io.embrace.android.exampleapp.paradigms.news.a2a

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.news.ui.NewsArticleDetailUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class NewsA2AArticleDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val articleId = intent.getStringExtra(EXTRA_ARTICLE_ID)
        val article = articleId?.let(SampleData::article)
        if (article == null) {
            Toast.makeText(this, "Unknown article", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent {
            ExampleAppTheme {
                NewsArticleDetailUi(
                    article = article,
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_ARTICLE_ID = "article_id"

        fun newIntent(context: Context, articleId: String): Intent =
            Intent(context, NewsA2AArticleDetailActivity::class.java)
                .putExtra(EXTRA_ARTICLE_ID, articleId)
    }
}
