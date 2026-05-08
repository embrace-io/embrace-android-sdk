package io.embrace.android.exampleapp.paradigms.bluesky.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.paradigms.bluesky.data.BlueskyFeedStore
import io.embrace.android.exampleapp.paradigms.social.ui.PostRow
import io.embrace.android.exampleapp.ui.appBarColors

/**
 * Read-only timeline that displays only Bluesky-fetched posts. The user fetches new posts via
 * a button in the header card; the cursor advances on each press so successive fetches paginate
 * deeper into the feed. Cleared via a button next to the count.
 *
 * Reuses [PostRow] from the social paradigm — the row composable is paradigm-agnostic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlueskyFeedTimelineUi(
    onPostClick: (postId: String) -> Unit,
    onAuthorClick: (handle: String) -> Unit,
    onBack: () -> Unit,
) {
    val posts by BlueskyFeedStore.posts.collectAsState()
    val isFetching by BlueskyFeedStore.isFetching.collectAsState()
    val fetchError by BlueskyFeedStore.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(fetchError) {
        if (fetchError != null) {
            snackbarHostState.showSnackbar("Bluesky fetch failed: $fetchError")
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Bluesky Live Feed") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "fetch_card") {
                FetchCard(
                    count = posts.size,
                    isFetching = isFetching,
                    onFetch = { BlueskyFeedStore.fetch() },
                    onClear = { BlueskyFeedStore.clear() },
                )
            }
            if (posts.isEmpty()) {
                item(key = "empty_state") {
                    EmptyState()
                }
            } else {
                items(items = posts, key = { it.id }) { post ->
                    PostRow(
                        post = post,
                        onPostClick = onPostClick,
                        onAuthorClick = onAuthorClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun FetchCard(
    count: Int,
    isFetching: Boolean,
    onFetch: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Bluesky live feed",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (count == 0) {
                    "No posts cached yet. Tap to pull 10 from the public \"What's Hot\" feed."
                } else {
                    "$count post${if (count == 1) "" else "s"} cached. Successive fetches paginate the feed."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onFetch,
                    enabled = !isFetching,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = if (isFetching) "Fetching…" else "Fetch 10 from Bluesky")
                }
                if (count > 0) {
                    OutlinedButton(onClick = onClear, enabled = !isFetching) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Tap fetch to pull live posts from Bluesky.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
