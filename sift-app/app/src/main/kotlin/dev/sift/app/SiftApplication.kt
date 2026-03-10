package dev.sift.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class SiftApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        initLogging()
        initSentry()
    }

    // ── WorkManager custom config (required for Hilt workers) ──────────
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG
                else android.util.Log.ERROR
            )
            .build()

    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashReportingTree())
        }
    }

    private fun initSentry() {
        if (BuildConfig.SENTRY_DSN.isNotBlank()) {
            SentryAndroid.init(this) { options ->
                options.dsn              = BuildConfig.SENTRY_DSN
                options.tracesSampleRate = 0.1   // 10% performance traces
                options.isDebug          = BuildConfig.DEBUG
                // Privacy: never send PII
                options.isSendDefaultPii = false
                options.maxBreadcrumbs   = 50
            }
        }
    }

    /** Release-only Timber tree — logs errors to Sentry. */
    private inner class CrashReportingTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority < android.util.Log.WARN) return
            if (t != null) io.sentry.Sentry.captureException(t)
        }
    }
}
