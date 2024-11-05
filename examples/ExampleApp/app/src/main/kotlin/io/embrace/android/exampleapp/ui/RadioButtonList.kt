package io.embrace.android.exampleapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> RadioButtonList(
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T) -> Unit,
) {
    LazyColumn {
        items(items.size) { index ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .wrapContentWidth()
                    .clickable { onItemSelected(items[index]) }
                    .padding(4.dp)
            ) {
                RadioButton(
                    selected = items[index] == selectedItem,
                    onClick = { onItemSelected(items[index]) }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = items[index].toString())
            }
        }
    }
}
