package io.embrace.android.exampleapp.paradigms.social.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.paradigms.data.Post
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.paradigms.ui.MediaItem
import io.embrace.android.exampleapp.ui.appBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailUi(
    post: Post,
    onAuthorClick: (handle: String) -> Unit,
    onBack: () -> Unit,
) {
    val sampleData = appGraph().sampleData
    val replies = remember(post.id) {
        sampleData.posts
            .filter { it.id != post.id && it.authorHandle != post.authorHandle }
            .shuffled(kotlin.random.Random(post.id.hashCode().toLong()))
            .take(8)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Post") },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "post_header") {
                PostDetailHeader(post = post, onAuthorClick = onAuthorClick)
            }
            item(key = "replies_header") {
                Text(
                    text = "${post.replyCount} replies",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
            }
            items(items = replies, key = { it.id }) { reply ->
                PostRow(
                    post = reply,
                    onPostClick = { /* nested replies not navigable in sample */ },
                    onAuthorClick = onAuthorClick,
                )
            }
        }
    }
}

@Composable
private fun PostDetailHeader(post: Post, onAuthorClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                avatar = post.authorAvatar,
                fallbackSeed = post.authorHandle.hashCode().toLong(),
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .clickable { onAuthorClick(post.authorHandle) },
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = post.authorDisplayName, style = MaterialTheme.typography.titleMedium)
                    if (post.isVerified) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Verified",
                            tint = Color(0xFF1DA1F2),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    text = "@${post.authorHandle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = post.body,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (post.media.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                post.media.forEach { item ->
                    MediaItem(media = item, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = post.timestampLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "↺ ${formatCount(post.repostCount)} reposts",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "♡ ${formatCount(post.likeCount)} likes",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "↩ ${formatCount(post.replyCount)} replies",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

