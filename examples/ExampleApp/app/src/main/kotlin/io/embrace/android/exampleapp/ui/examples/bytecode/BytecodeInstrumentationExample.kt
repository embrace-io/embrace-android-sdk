package io.embrace.android.exampleapp.ui.examples.bytecode

import android.content.Intent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlin.reflect.KClass

private data class BytecodeSample(
    val name: String,
    val clz: KClass<*>,
)

private val items = listOf(
    BytecodeSample("OnClick/OnLongClick", BytecodeViewClickActivity::class),
    BytecodeSample("WebView", BytecodeWebViewActivity::class),
    BytecodeSample("OkHttp", BytecodeOkHttpActivity::class),
)

@Composable
fun BytecodeInstrumentationExample() {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    items.forEach {
        Button(onClick = {
            val intent = Intent(ctx, it.clz.java)
            ctx.startActivity(intent)
        }) {
            Text(it.name)
        }
    }
}
