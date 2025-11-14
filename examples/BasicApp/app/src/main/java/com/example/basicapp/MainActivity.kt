package com.example.basicapp

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.basicapp.databinding.ActivityMainBinding
import io.embrace.android.embracesdk.Embrace
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

@OptIn(ExperimentalApi::class)
class MainActivity : AppCompatActivity() {
    private val hucExecutor = Executors.newFixedThreadPool(2)
    private val url = "https://www.google.com"
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val tracer = Embrace.getOpenTelemetryKotlin().tracerProvider.getTracer("app-tracer")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            val span = tracer.createSpan("fab-click")
            hucExecutor.submit {
                var connection: HttpsURLConnection? = null
                try {
                    connection = URL(url).openConnection() as HttpsURLConnection
                    connection.setRequestProperty("Content-Type", String.format("application/json;charset=%s", StandardCharsets.UTF_8))
                    connection.connectTimeout = 5000
                    connection.readTimeout = 15000

                    val responseCode = connection.responseCode
                    if (responseCode == 200) {
                        Embrace.addBreadcrumb("Successful request to $url")
                    } else {
                        Embrace.addBreadcrumb("Non-successful request to $url with response code $responseCode")
                    }
                } catch (t: Throwable) {
                    Embrace.addBreadcrumb("Error while making HUC network request: $t")
                } finally {
                    connection?.disconnect()
                }
            }
            span.end()
        }
    }

    override fun onResume() {
        super.onResume()
        Embrace.recordSpan("resume-breadcrumb") {
            val s = tracer.createSpan("otel-kotlin-api-span")
            Embrace.addBreadcrumb("MainActivity resumed")
            s.end()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
            || super.onSupportNavigateUp()
    }
}
