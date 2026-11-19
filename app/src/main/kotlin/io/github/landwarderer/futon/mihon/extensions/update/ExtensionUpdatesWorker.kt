package io.github.landwarderer.futon.mihon.extensions.update

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.mihon.MihonExtensionLoader
import io.github.landwarderer.futon.mihon.extensions.repo.ExternalExtensionRepoRepository
import io.github.landwarderer.futon.mihon.extensions.repo.ExternalExtensionType
import io.github.landwarderer.futon.settings.sources.extension.ExtensionDownloaderActivity
import kotlinx.coroutines.CancellationException

/**
 * Fetch metadata only. Installation remains a separate user-initiated action in the manager.
 */
@HiltWorker
class ExtensionUpdatesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val loader: MihonExtensionLoader,
    private val repositories: ExternalExtensionRepoRepository,
    private val settings: AppSettings,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val installed = loader.getInstalledExtensions(context).associate { it.pkgName to it.versionCode }
        val available = repositories.getAvailableExtensions(ExternalExtensionType.MIHON)
        val repos = repositories.getByType(ExternalExtensionType.MIHON)
        if (!canCompleteUpdateCheck(repos.map { it.lastError })) {
            Result.retry()
        } else {
            val packages = newerInstalledPackages(installed, available.map { it.pkgName to it.versionCode })
            // Several repositories can list one package; announce the newest version of each once.
            val updates = available.filter { it.pkgName in packages }
                .groupBy { it.pkgName }.map { (_, versions) -> versions.maxBy { it.versionCode } }
            val signature = updateSignature(updates.map { it.pkgName to it.versionCode })
            settings.pendingExtensionUpdates = signature
            if (updates.isEmpty()) {
                settings.lastNotifiedExtensionUpdates = null
            } else if (settings.isExtensionUpdateNotificationsEnabled &&
                signature != settings.lastNotifiedExtensionUpdates && notifyUpdates(updates.map { it.name })
            ) {
                settings.lastNotifiedExtensionUpdates = signature
            }
            settings.lastExtensionUpdateCheck = System.currentTimeMillis()
            Result.success()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }

    private fun notifyUpdates(names: List<String>): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val system = context.getSystemService(NotificationManager::class.java)
            system.createNotificationChannel(android.app.NotificationChannel(CHANNEL, context.getString(R.string.extension_updates_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(context, ExtensionDownloaderActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_book_plus)
            .setContentTitle(context.getString(R.string.extension_updates_available))
            .setContentText(names.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(names.joinToString(", ")))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false // Permission may change.
        }
    }

    companion object {
        private const val CHANNEL = "mihon_extension_updates"
        private const val NOTIFICATION_ID = 817221
        private const val WORK_NAME = "mihon-extension-update-check"
        private const val MIN_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

        fun schedule(context: Context, settings: AppSettings) {
            val last = settings.lastExtensionUpdateCheck
            val now = System.currentTimeMillis()
            if (!shouldCheckForUpdates(now, last, MIN_CHECK_INTERVAL_MS)) return
            val request = OneTimeWorkRequestBuilder<ExtensionUpdatesWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}

internal fun newerInstalledPackages(installed: Map<String, Long>, available: List<Pair<String, Long>>): Set<String> =
    available.filter { (pkg, version) -> installed[pkg]?.let { version > it } == true }.mapTo(linkedSetOf()) { it.first }

internal fun shouldCheckForUpdates(now: Long, last: Long, interval: Long): Boolean =
    last <= 0 || now < last || now - last >= interval

internal fun canCompleteUpdateCheck(repositoryErrors: List<String?>): Boolean = repositoryErrors.none { it != null }

/** Identifies one set of pending updates; the order of the list does not matter. */
internal fun updateSignature(updates: List<Pair<String, Long>>): String =
    updates.sortedBy { it.first }.joinToString(",") { "${it.first}:${it.second}" }

/** Reads what [updateSignature] wrote: package to the newest version found in a repository. */
internal fun parseUpdateSignature(signature: String?): Map<String, Long> {
    if (signature.isNullOrEmpty()) return emptyMap()
    return signature.split(',').mapNotNull { entry ->
        val pkg = entry.substringBeforeLast(':', "")
        val version = entry.substringAfterLast(':', "").toLongOrNull()
        if (pkg.isEmpty() || version == null) null else pkg to version
    }.toMap()
}
