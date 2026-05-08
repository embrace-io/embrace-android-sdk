package io.embrace.android.exampleapp.paradigms

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.ComplexDestinationActivity
import io.embrace.android.exampleapp.MainFragmentActivity
import io.embrace.android.exampleapp.ObservedBackStackActivity
import io.embrace.android.exampleapp.ObservedNavControllerActivity

private data class NavigationTestingEntry(
    val label: String,
    val activityClass: Class<out Activity>,
)

private val NAVIGATION_TESTING_ENTRIES: List<NavigationTestingEntry> = listOf(
    NavigationTestingEntry("Compose Fragment Navigation", MainFragmentActivity::class.java),
    NavigationTestingEntry("Complex Fragment Navigation", ComplexDestinationActivity::class.java),
    NavigationTestingEntry(
        "Observed NavController (Compose Nav 2.x)",
        ObservedNavControllerActivity::class.java,
    ),
    NavigationTestingEntry("Observed BackStack (Nav 3)", ObservedBackStackActivity::class.java),
)

@Composable
fun ParadigmHubContent() {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header_navigation_testing") {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    text = "Navigation Testing",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                HorizontalDivider()
            }
        }
        items(
            items = NAVIGATION_TESTING_ENTRIES,
            key = { it.label },
        ) { entry ->
            Button(
                onClick = {
                    context.startActivity(Intent(context, entry.activityClass))
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                Text(text = entry.label)
            }
        }
        NavParadigm.entries.forEach { paradigm ->
            item(key = "header_${paradigm.name}") {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        text = paradigm.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    HorizontalDivider()
                }
            }
            items(
                items = ParadigmCatalog.runsFor(paradigm),
                key = { it.key },
            ) { run ->
                Button(
                    onClick = { run.launch(context) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                ) {
                    Text(text = run.style.displayName)
                }
            }
        }
    }
}
