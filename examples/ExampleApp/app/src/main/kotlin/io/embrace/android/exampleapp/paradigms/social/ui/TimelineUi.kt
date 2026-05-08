package io.embrace.android.exampleapp.paradigms.social.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.paradigms.data.Post
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
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(items = posts, key = { it.id }) { post ->
                PostRow(
                    post = post,
                    onPostClick = onPostClick,
                    onAuthorClick = onAuthorClick,
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PostRow(
    post: Post,
    onPostClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPostClick(post.id) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { onAuthorClick(post.authorHandle) }) {
                Text(
                    text = post.authorDisplayName,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = " @${post.authorHandle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = post.body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(text = "↺ ${post.repostCount}", style = MaterialTheme.typography.labelSmall)
                Text(text = "♡ ${post.likeCount}", style = MaterialTheme.typography.labelSmall)
                Text(text = "↩ ${post.replyCount}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
