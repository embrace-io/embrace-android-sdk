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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.paradigms.bluesky.data.BlueskyFeedStore
import io.embrace.android.exampleapp.paradigms.bluesky.data.BlueskyPinnedFeed
import io.embrace.android.exampleapp.paradigms.data.MediaRef
import io.embrace.android.exampleapp.paradigms.social.ui.PostRow
import io.embrace.android.exampleapp.ui.appBarColors
import kotlinx.coroutines.launch

/**
 * Read-only timeline that displays Bluesky-fetched posts. A [TabRow] and a [HorizontalPager] both
 * map onto the pinned [BlueskyPinnedFeed]s — the user can either tap a tab or swipe horizontally
 * to switch feeds. Each page has its own posts/cursor/cache file in the store; pull-to-refresh and
 * the FetchCard buttons act on the currently visible page only.
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
    val store = appGraph().blueskyFeedStore
    val feeds = BlueskyPinnedFeed.entries
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { feeds.size })
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentFeed = feeds[pagerState.currentPage]
    val currentError by store.error(currentFeed).collectAsState()
    LaunchedEffect(currentFeed, currentError) {
        if (currentError != null) {
            snackbarHostState.showSnackbar("Bluesky fetch failed: $currentError")
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
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
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    feeds.forEachIndexed { idx, feed ->
                        Tab(
                            selected = idx == pagerState.currentPage,
                            onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(idx) }
                            },
                            text = { Text(feed.label) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { page ->
            FeedPage(
                feed = feeds[page],
                store = store,
                autoplayEnabled = pagerState.settledPage == page,
                onPostClick = onPostClick,
                onAuthorClick = onAuthorClick,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedPage(
    feed: BlueskyPinnedFeed,
    store: BlueskyFeedStore,
    autoplayEnabled: Boolean,
    onPostClick: (postId: String) -> Unit,
    onAuthorClick: (handle: String) -> Unit,
) {
    val posts by store.posts(feed).collectAsState()
    val isFetching by store.isFetching(feed).collectAsState()
    val lazyState = rememberLazyListState()
    /**
     * The id of the topmost post on this page that contains a video AND is at least 50% visible.
     * Disabled when [autoplayEnabled] is false (i.e. this page isn't the settled pager page) so
     * only the foreground page ever has a playing video.
     */
    val activeVideoPostId: String? by remember(posts, autoplayEnabled) {
        derivedStateOf {
            if (!autoplayEnabled) return@derivedStateOf null
            val viewportTop = lazyState.layoutInfo.viewportStartOffset
            val viewportBottom = lazyState.layoutInfo.viewportEndOffset
            lazyState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { info ->
                val key = info.key as? String ?: return@firstNotNullOfOrNull null
                val post = posts.firstOrNull { "${feed.slug}_${it.id}" == key }
                    ?: return@firstNotNullOfOrNull null
                if (post.media.none { it is MediaRef.Video }) return@firstNotNullOfOrNull null
                val itemTop = info.offset
                val itemBottom = info.offset + info.size
                val visible = (minOf(itemBottom, viewportBottom) - maxOf(itemTop, viewportTop))
                    .coerceAtLeast(0)
                if (info.size > 0 && visible * 2 >= info.size) post.id else null
            }
        }
    }
    PullToRefreshBox(
        isRefreshing = isFetching,
        onRefresh = { store.fetch(feed) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "fetch_card_${feed.slug}") {
                FetchCard(
                    feed = feed,
                    count = posts.size,
                    isFetching = isFetching,
                    onFetch = { store.fetch(feed) },
                    onClear = { store.clear(feed) },
                )
            }
            if (posts.isEmpty()) {
                item(key = "empty_state_${feed.slug}") {
                    EmptyState()
                }
            } else {
                items(items = posts, key = { "${feed.slug}_${it.id}" }) { post ->
                    PostRow(
                        post = post,
                        onPostClick = onPostClick,
                        onAuthorClick = onAuthorClick,
                        isActiveVideoSlot = post.id == activeVideoPostId,
                    )
                }
            }
        }
    }
}

@Composable
private fun FetchCard(
    feed: BlueskyPinnedFeed,
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
                text = "Bluesky · ${feed.label}",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (count == 0) {
                    "No posts cached yet. Pull to refresh, or tap below to pull 30 from this feed."
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
                    Text(text = if (isFetching) "Fetching…" else "Fetch 30 from ${feed.label}")
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
