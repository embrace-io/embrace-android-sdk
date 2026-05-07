package io.embrace.android.exampleapp.paradigms.social.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.paradigms.data.MediaRef
import io.embrace.android.exampleapp.paradigms.data.Post
import io.embrace.android.exampleapp.paradigms.ui.MediaItem
import io.embrace.android.exampleapp.paradigms.ui.ProceduralImage
import io.embrace.android.exampleapp.ui.appBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineUi(
    title: String,
    posts: List<Post>,
    onPostClick: (postId: String) -> Unit,
    onAuthorClick: (handle: String) -> Unit,
    onCompose: () -> Unit,
    onBack: (() -> Unit)? = null,
    postedBody: String? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(postedBody) {
        if (postedBody != null) {
            snackbarHostState.showSnackbar("Posted: $postedBody")
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = appBarColors(),
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCompose) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Compose post")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "trending_card") {
                TrendingCard()
            }
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

@Composable
private fun TrendingCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFB300))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Trending today", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            listOf(
                "kotlin 2.3" to "32.4k posts",
                "android nav3" to "11.7k posts",
                "embrace observability" to "4.1k posts",
                "compose perf" to "9.8k posts",
            ).forEach { (tag, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "#$tag", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = count,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PostRow(
    post: Post,
    onPostClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable { onPostClick(post.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            if (post.isPinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Pinned",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.Top) {
                Avatar(
                    seed = post.authorHandle.hashCode().toLong(),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onAuthorClick(post.authorHandle) },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    PostHeaderRow(post = post, onAuthorClick = onAuthorClick)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = post.body,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 8,
                    )
                    if (post.media.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        MediaGallery(media = post.media)
                    }
                    if (post.mentions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            post.mentions.take(3).forEach { handle ->
                                MentionChip(handle = handle, onClick = { onAuthorClick(handle) })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    PostActionRow(post = post)
                }
            }
        }
    }
}

@Composable
private fun PostHeaderRow(
    post: Post,
    onAuthorClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAuthorClick(post.authorHandle) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = post.authorDisplayName, style = MaterialTheme.typography.titleSmall)
        if (post.isVerified) {
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Verified",
                tint = Color(0xFF1DA1F2),
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = " @${post.authorHandle}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = " · ${post.timestampLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MediaGallery(media: List<MediaRef>) {
    when {
        media.size == 1 -> {
            MediaItem(
                media = media.first(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        media.size == 2 -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                media.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        MediaItem(media = item)
                    }
                }
            }
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MediaItem(
                    media = media.first(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    media.drop(1).take(3).forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            MediaItem(media = item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MentionChip(handle: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = "@$handle",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun PostActionRow(post: Post) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ActionPill(
            icon = Icons.Filled.Favorite,
            count = post.likeCount,
        )
        ActionPill(
            icon = Icons.Filled.Star,
            count = post.repostCount,
        )
        ActionPill(
            icon = Icons.AutoMirrored.Filled.Send,
            count = post.replyCount,
        )
        ActionPill(
            icon = Icons.Filled.Star,
            count = (post.likeCount + post.repostCount * 2) / 7,
        )
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = formatCount(count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun Avatar(seed: Long, modifier: Modifier = Modifier) {
    ProceduralImage(seed = seed, modifier = modifier)
}

internal fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M"
    count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K"
    else -> count.toString()
}
