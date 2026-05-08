package io.embrace.android.exampleapp.paradigms.social.a2a

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.social.ui.ProfileUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class SocialA2AProfileActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val handle = intent.getStringExtra(EXTRA_HANDLE)
        val author = handle?.let(SampleData::author)
        if (author == null) {
            Toast.makeText(this, "Unknown author", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val authorPosts = SampleData.posts.filter { it.authorHandle == author.handle }
        setContent {
            ExampleAppTheme {
                ProfileUi(
                    author = author,
                    authorPosts = authorPosts,
                    onPostClick = { id ->
                        startActivity(SocialA2APostDetailActivity.newIntent(this, id))
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_HANDLE = "handle"

        fun newIntent(context: Context, handle: String): Intent =
            Intent(context, SocialA2AProfileActivity::class.java)
                .putExtra(EXTRA_HANDLE, handle)
    }
}
