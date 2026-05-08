package io.embrace.android.exampleapp.ui.examples

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.paradigms.bluesky.BlueskyFeedActivity

@Composable
fun BlueskyFeedExample() {
    val context = LocalContext.current
    Text(
        "Opens a standalone Bluesky live-feed app. Posts are fetched from the public " +
            "`app.bsky.feed.getFeed` endpoint and persisted to `cacheDir/social/dynamic_posts.json`. " +
            "Successive fetches paginate via the cursor; \"Clear\" deletes the cache file."
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = { context.startActivity(BlueskyFeedActivity.newIntent(context)) }) {
        Text("Open Bluesky Feed")
    }
}
