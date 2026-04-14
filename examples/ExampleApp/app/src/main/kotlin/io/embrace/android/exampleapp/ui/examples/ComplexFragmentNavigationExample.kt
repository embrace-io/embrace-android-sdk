package io.embrace.android.exampleapp.ui.examples

import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.ComplexDestinationActivity

@Composable
fun ComplexFragmentNavigationExample() {
    val context = LocalContext.current
    Text(
        "Opens a FragmentActivity with a NavController that exercises every complex destination format: " +
            "argument templates, dialogs, nested graphs, and @Serializable routes with and without @SerialName."
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = {
        context.startActivity(Intent(context, ComplexDestinationActivity::class.java))
    }) {
        Text("Open Complex Navigation Activity")
    }
}
