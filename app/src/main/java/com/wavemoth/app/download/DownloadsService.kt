package com.wavemoth.app.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.wavemoth.app.MainActivity
import com.wavemoth.app.R
import com.wavemoth.app.WavemothApp

/** Keeps downloads running with the app in the background, with a progress notification. */
@UnstableApi
class DownloadsService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.downloads_channel,
    0,
) {
    private val notifications by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = (application as WavemothApp).downloads.manager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val left = downloads.count { it.state != Download.STATE_COMPLETED }
        return notifications.buildProgressNotification(
            this, R.drawable.ic_stat_download, openApp,
            if (left > 0) "$left songs left" else null,
            downloads, notMetRequirements,
        )
    }

    private companion object {
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "downloads"
    }
}
