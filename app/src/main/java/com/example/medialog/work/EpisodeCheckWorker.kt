package com.example.medialog.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.medialog.MediaLogApplication
import com.example.medialog.MainActivity
import com.example.medialog.R
import com.example.medialog.data.local.entity.EntryStatus
import com.example.medialog.data.repository.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class EpisodeCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val mediaRepository: MediaRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting episode check...")

            val ongoingEntries = mediaRepository.getAllEntries()
                .filter { it.status == EntryStatus.ONGOING }

            Log.d(TAG, "Checking ${ongoingEntries.size} ongoing entries")

            for (entry in ongoingEntries) {
                try {
                    val remoteDetail = mediaRepository.getRemoteDetail(entry.type, entry.externalId)
                    val nextEpisode = remoteDetail?.nextEpisode
                    val nextAiringAt = remoteDetail?.nextEpisodeAiringAt

                    if (nextEpisode != null && nextAiringAt != null) {
                        val airingTimeMillis = nextAiringAt * 1000
                        val now = System.currentTimeMillis()

                        // Episode sudah tayang dan progress belum sampai
                        if (airingTimeMillis <= now && entry.progress < nextEpisode) {
                            Log.d(TAG, "New episode available for ${entry.title}: EP $nextEpisode")
                            showNotification(entry.title, nextEpisode, entry.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to check ${entry.title}", e)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Episode check failed", e)
            Result.failure()
        }
    }

    private fun showNotification(title: String, episode: Int, entryId: Long) {
        val context = applicationContext

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("entry_id", entryId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            entryId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MediaLogApplication.CHANNEL_EPISODE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Episode baru tayang! 🎬")
            .setContentText("$title - Episode $episode sudah tersedia")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(entryId.toInt(), notification)
    }

    companion object {
        private const val TAG = "EpisodeCheckWorker"
    }
}
