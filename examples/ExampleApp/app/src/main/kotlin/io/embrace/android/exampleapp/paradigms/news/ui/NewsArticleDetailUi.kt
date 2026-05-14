package io.embrace.android.exampleapp.paradigms.news.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.embrace.android.exampleapp.paradigms.data.Article
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.paradigms.ui.ImageItem
import io.embrace.android.exampleapp.ui.appBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsArticleDetailUi(
    article: Article,
    onBack: () -> Unit,
) {
    val sampleData = appGraph().sampleData
    val related = remember(article.id) {
        sampleData.articles
            .filter { it.id != article.id && it.sectionId == article.sectionId }
            .shuffled(kotlin.random.Random(article.id.hashCode().toLong()))
            .take(4)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Article") },
                colors = appBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (article.heroImage != null) {
                ImageItem(
                    source = article.heroImage,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = article.tags.firstOrNull() ?: "News",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = article.headline, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = article.summary,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = article.byline,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = article.publishedAtIso,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${article.readTimeMinutes} min read",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                if (article.webContentHtml != null) {
                    HtmlBlock(html = article.webContentHtml, estimatedHeightDp = 320)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                article.paragraphs.forEachIndexed { index, paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    if (index == article.paragraphs.size / 2 && article.pullQuote != null) {
                        PullQuote(text = article.pullQuote)
                    }
                }
                if (article.tags.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TagFlow(tags = article.tags)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "More from this section",
                    style = MaterialTheme.typography.titleSmall,
                )
                related.forEach { rel ->
                    RelatedArticleCard(article = rel)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HtmlBlock(html: String, estimatedHeightDp: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(estimatedHeightDp.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.builtInZoomControls = false
                    settings.useWideViewPort = false
                    webViewClient = WebViewClient()
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(
                    /* baseUrl = */ null,
                    /* data = */ html,
                    /* mimeType = */ "text/html",
                    /* encoding = */ "utf-8",
                    /* historyUrl = */ null,
                )
            },
        )
    }
}

@Composable
private fun PullQuote(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = "\"$text\"",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun TagFlow(tags: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.take(6).forEach { tag ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun RelatedArticleCard(article: Article) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { /* could re-navigate */ },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
            if (article.heroImage != null) {
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 60.dp)
                        .clip(RoundedCornerShape(6.dp)),
                ) {
                    ImageItem(
                        source = article.heroImage,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column {
                Text(text = article.headline, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${article.readTimeMinutes} min · ${article.publishedAtIso}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun WebViewBackground() {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface))
}
