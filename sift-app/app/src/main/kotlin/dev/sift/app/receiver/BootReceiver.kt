package dev.sift.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import dev.sift.app.worker.EmbeddingWorker
import dev.sift.app.worker.PruneWorker
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            Timber.d("BootReceiver: rescheduling workers")
            val wm = WorkManager.getInstance(context)
            EmbeddingWorker.schedule(wm)
            PruneWorker.schedule(wm)
        }
    }
}
