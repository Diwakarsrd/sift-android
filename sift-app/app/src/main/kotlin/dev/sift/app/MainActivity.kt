// ── MainActivity.kt ───────────────────────────────────────────────────────
package dev.sift.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import dev.sift.app.ui.SearchScreen
import dev.sift.app.ui.SearchViewModel
import dev.sift.app.ui.theme.SiftTheme
import dev.sift.app.worker.EmbeddingWorker
import dev.sift.app.worker.PruneWorker
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels()
    @Inject lateinit var workManager: WorkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        scheduleBackgroundWork()

        setContent {
            SiftTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val state by viewModel.state.collectAsState()
                    SearchScreen(state = state, onEvent = viewModel::onEvent)
                }
            }
        }
    }

    private fun scheduleBackgroundWork() {
        EmbeddingWorker.schedule(workManager)
        PruneWorker.schedule(workManager)
    }
}
