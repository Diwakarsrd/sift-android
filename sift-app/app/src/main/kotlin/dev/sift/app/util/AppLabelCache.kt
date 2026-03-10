package dev.sift.app.util

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLabelCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = ConcurrentHashMap<String, String>()
    private val pm    = context.packageManager

    fun getLabel(packageName: String): String =
        cache.getOrPut(packageName) {
            try {
                pm.getApplicationInfo(packageName, 0)
                    .let { pm.getApplicationLabel(it).toString() }
            } catch (_: PackageManager.NameNotFoundException) {
                packageName.substringAfterLast(".")
            }
        }
}
