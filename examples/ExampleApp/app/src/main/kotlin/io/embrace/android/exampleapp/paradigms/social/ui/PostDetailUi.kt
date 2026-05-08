package io.embrace.android.exampleapp.paradigms.social.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.paradigms.data.Post
import io.embrace.android.exampleapp.ui.appBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailUi(
    post: Post,
    onAuthorClick: (handle: String) -> Unit,
    onBack: () -> Unit,
) {
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth().clickable { onAuthorClick(post.authorHandle) }) {
                Text(
                    text = post.authorDisplayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = " @${post.authorHandle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = post.body,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(text = "↺ ${post.repostCount} reposts", style = MaterialTheme.typography.labelMedium)
                Text(text = "♡ ${post.likeCount} likes", style = MaterialTheme.typography.labelMedium)
                Text(text = "↩ ${post.replyCount} replies", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
