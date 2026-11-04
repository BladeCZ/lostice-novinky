package cz.lostice.novinky.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cz.lostice.novinky.MainActivity
import cz.lostice.novinky.R
import cz.lostice.novinky.data.Category
import cz.lostice.novinky.data.Prefs
import cz.lostice.novinky.data.Repository
import java.util.concurrent.TimeUnit

/** Pravidelně kontroluje nové položky a podle nastavení zobrazí upozornění. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = Repository(applicationContext)
        val prefs = Prefs(applicationContext)
        var anyFailure = false
        for (c in Category.values()) {
            val feed = repo.refresh(c)
            if (feed == null) {
                anyFailure = true
                continue
            }
            val ids = feed.items.map { it.id }.toSet()
            val seen = prefs.seen(c)
            if (seen != null && prefs.notifyEnabled(c)) {
                val fresh = feed.items.filter { it.id !in seen }
                if (fresh.isNotEmpty()) notify(applicationContext, c, fresh.map { it.title })
            }
            prefs.setSeen(c, (seen ?: emptySet()) + ids)
        }
        return if (anyFailure) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "novinky_periodic_check"
        private const val CHANNEL_ID = "novinky"
        const val EXTRA_TAB = "tab"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        private fun notify(ctx: Context, c: Category, titles: List<String>) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Novinky z Loštic", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val intent = Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_TAB, c.ordinal)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(
                ctx, c.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = if (titles.size == 1) titles[0] else "${titles.size} nových položek"
            val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(c.notifyTitle)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(titles.joinToString("\n")))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(1000 + c.ordinal, n)
        }
    }
}
