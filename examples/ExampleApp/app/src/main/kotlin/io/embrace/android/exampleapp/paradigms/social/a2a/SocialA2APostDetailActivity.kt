package io.embrace.android.exampleapp.paradigms.social.a2a

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.social.ui.PostDetailUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class SocialA2APostDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val postId = intent.getStringExtra(EXTRA_POST_ID)
        val post = postId?.let(SampleData::post)
        if (post == null) {
            Toast.makeText(this, "Unknown post", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent {
            ExampleAppTheme {
                PostDetailUi(
                    post = post,
                    onAuthorClick = { handle ->
                        startActivity(SocialA2AProfileActivity.newIntent(this, handle))
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_POST_ID = "post_id"

        fun newIntent(context: Context, postId: String): Intent =
            Intent(context, SocialA2APostDetailActivity::class.java)
                .putExtra(EXTRA_POST_ID, postId)
    }
}
