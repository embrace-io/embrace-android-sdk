package io.embrace.android.exampleapp.paradigms.news.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.paradigms.data.NewsSection
import io.embrace.android.exampleapp.ui.appBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsSectionsUi(
    title: String,
    sections: List<NewsSection>,
    onSectionClick: (sectionId: String) -> Unit,
    onDeeplinkRandom: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(title) }, colors = appBarColors())
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (onDeeplinkRandom != null) {
                Button(
                    onClick = onDeeplinkRandom,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Deeplink: open a random article")
                }
                HorizontalDivider()
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = sections, key = { it.id }) { section ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSectionClick(section.id) }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Text(text = section.title, style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
