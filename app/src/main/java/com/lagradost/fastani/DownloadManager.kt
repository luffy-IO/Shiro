package com.lagradost.fastani

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bumptech.glide.Glide
import com.lagradost.fastani.MainActivity.Companion.activity
import com.lagradost.fastani.MainActivity.Companion.getColorFromAttr
import com.lagradost.fastani.MainActivity.Companion.isDonor
import kotlin.concurrent.thread
import kotlin.math.pow
import kotlin.math.round
import java.lang.Exception
import java.net.URL
import java.net.URLConnection
import java.io.*
import java.util.concurrent.Executors

const val UPDATE_TIME = 1000
const val CHANNEL_ID = "fastani.general"
const val CHANNEL_NAME = "General"
const val CHANNEL_DESCRIPT = "The notification channel for the fastani app"


class DownloadService : IntentService("DownloadService") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val id = intent.getIntExtra("id", -1)
            val type = intent.getStringExtra("type")
            if (id != -1 && type != null) {
                DownloadManager.downloadMustUpdateStatus[id] = true
                DownloadManager.downloadStatus[id] = when (type) {
                    "resume" -> DownloadManager.DownloadStatusType.IsDownloading
                    "pause" -> DownloadManager.DownloadStatusType.IsPaused
                    "stop" -> DownloadManager.DownloadStatusType.IsStoped
                    else -> DownloadManager.DownloadStatusType.IsDownloading
                }
            }
        }
    }
}

object DownloadManager {
    private var localContext: Context? = null
    val downloadStatus = hashMapOf<Int, DownloadStatusType>()
    val downloadMustUpdateStatus = hashMapOf<Int, Boolean>()

    fun init(_context: Context) {
        localContext = _context
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = CHANNEL_NAME //getString(R.string.channel_name)
            val descriptionText = CHANNEL_DESCRIPT//getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                localContext!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    data class DownloadInfo(
        val card: FastAniApi.Card,
        val seasonIndex: Int,
        val episodeIndex: Int,
    )

    enum class DownloadType {
        IsPaused,
        IsDownloading,
        IsDone,
        IsFailed,
        IsStopped,
    }

    enum class DownloadActionType {
        Pause,
        Resume,
        Stop,
    }

    enum class DownloadStatusType {
        IsPaused,
        IsDownloading,
        IsStoped,
    }


    fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return round(this * multiplier) / multiplier
    }

    fun convertBytesToAny(bytes: Long, digits: Int = 2, steps: Double = 3.0): Double {
        return (bytes / 1024.0.pow(steps)).round(digits)
    }

    val cachedBitmaps = hashMapOf<String, Bitmap>()

    fun getImageBitmapFromUrl(url: String): Bitmap? {
        if (cachedBitmaps.containsKey(url)) {
            return cachedBitmaps[url]
        }

        val bitmap = Glide.with(localContext!!)
            .asBitmap()
            .load(url).into(1080, 720) // Width and height
            .get()
        if (bitmap != null) {
            cachedBitmaps[url] = bitmap
        }
        return null
    }

    fun censorFilename(_name: String, toLower: Boolean = false): String {
        val rex = Regex.fromLiteral("[^A-Za-z0-9\\.\\-\\: ]")
        var name = _name
        rex.replace(name, "")//Regex.Replace(name, @"[^A-Za-z0-9\.]+", String.Empty)
        name.replace(" ", "")
        if (toLower) {
            name = name.toLowerCase()
        }
        return name
    }

    fun downloadEpisode(info: DownloadInfo) {
        if (!isDonor) {
            Toast.makeText(activity, "This is for donors only.", Toast.LENGTH_SHORT).show()
            return
        }
        thread {
            val id = (info.card.anilistId + "S${info.seasonIndex}E${info.episodeIndex}").hashCode()

            val isMovie: Boolean = info.card.episodes == 1 && info.card.status == "FINISHED"
            val ep = info.card.cdnData.seasons[info.seasonIndex].episodes[info.episodeIndex]
            var title = ep.title
            if (title?.replace(" ", "") == "") {
                title = "Episode " + info.episodeIndex + 1
            }

            val path = activity!!.filesDir.toString() +
                    "/Download/Anime/" +
                    censorFilename(info.card.title.english) +
                    if (isMovie)
                        ".mp4"
                    else
                        "/" + censorFilename("S${info.seasonIndex + 1}:E${info.episodeIndex + 1} $title") + ".mp4"

            println("FULL DLOAD PATH: " + path)
            val rFile: File = File(path)

            try {
                rFile.mkdirs()
            } catch (_ex: Exception) {
                println("FAILED:::$_ex")
            }
            val url = ep.file

            val _url = URL(url)

            val connection: URLConnection = _url.openConnection()

            val resumeIntent = false // TODO FIX

            var bytesRead = 0L
            val referer = ""

            try {
                if (!rFile.exists()) {
                    println("FILE DOESN'T EXITS")
                    rFile.createNewFile()
                } else {
                    if (resumeIntent) {
                        bytesRead = rFile.length()
                        connection.setRequestProperty("Range", "bytes=" + rFile.length() + "-")
                    } else {
                        rFile.delete()
                        rFile.createNewFile()
                    }
                }
            } catch (e: Exception) {
                println(e)
                activity?.runOnUiThread {
                    Toast.makeText(localContext!!, "Permission error", Toast.LENGTH_SHORT).show()
                }
                return@thread
            }

            connection.setRequestProperty("Accept-Encoding", "identity")
            if (referer != "") {
                connection.setRequestProperty("Referer", referer)
            }
            connection.connectTimeout = 10000
            var clen = 0
            try {
                connection.connect()
                clen = connection.contentLength
            } catch (_ex: Exception) {
                println("CONNECT:::$_ex")
            }

            if (clen < 5000000) { // min of 5 MB
                clen = 0
            }
            if (clen <= 0) { // TO SMALL OR INVALID
                showNot(0, 0, 0, DownloadType.IsFailed, info)
                return@thread
            }

            downloadStatus[id] = DownloadStatusType.IsDownloading
            val bytesTotal: Long = (clen + bytesRead.toInt()).toLong()
            val input: InputStream = BufferedInputStream(connection.inputStream)
            val output: OutputStream = FileOutputStream(rFile, true)
            var bytesPerSec = 0L
            val buffer: ByteArray = ByteArray(1024)
            var count = 0
            var lastUpdate = System.currentTimeMillis()

            while (true) {
                try {
                    count = input.read(buffer)
                    if (count < 0) break

                    bytesRead += count
                    bytesPerSec += count
                    output.write(buffer, 0, count)
                    val currentTime = System.currentTimeMillis()
                    val timeDiff = currentTime - lastUpdate
                    val contains = downloadMustUpdateStatus.containsKey(id)
                    if (timeDiff > UPDATE_TIME || contains) {
                        if (contains) {
                            downloadMustUpdateStatus.remove(id)
                        }

                        if (downloadStatus[id] == DownloadStatusType.IsStoped) {
                            downloadStatus.remove(id)
                            rFile.delete()
                            showNot(0, bytesTotal, 0, DownloadType.IsStopped, info)
                            output.flush()
                            output.close()
                            input.close()
                            return@thread
                        } else {
                            showNot(
                                bytesRead,
                                bytesTotal,
                                (bytesPerSec * UPDATE_TIME) / timeDiff,

                                if (downloadStatus[id] == DownloadStatusType.IsPaused)
                                    DownloadType.IsPaused
                                else
                                    DownloadType.IsDownloading,

                                info
                            )
                            lastUpdate = currentTime
                            bytesPerSec = 0
                            try {
                                while (downloadStatus[id] == DownloadStatusType.IsPaused) {
                                    Thread.sleep(100)
                                }
                            } catch (e: Exception) {
                            }
                        }
                    }
                } catch (_ex: Exception) {
                    println("CONNECT TRUE:::$_ex")
                    showNot(bytesRead, bytesTotal, 0, DownloadType.IsFailed, info)
                }
            }
            showNot(bytesRead, bytesTotal, 0, DownloadType.IsDone, info)
            output.flush()
            output.close()
            input.close()
            downloadStatus.remove(id)
        }
    }

    private fun showNot(progress: Long, total: Long, progressPerSec: Long, type: DownloadType, info: DownloadInfo) {
        val isMovie: Boolean = info.card.episodes == 1 && info.card.status == "FINISHED"

        // Create an explicit intent for an Activity in your app
        val intent = Intent(localContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(localContext, 0, intent, 0)

        val progressPro = minOf(maxOf((progress * 100 / maxOf(total, 1)).toInt(), 0), 100)

        val ep = info.card.cdnData.seasons[info.seasonIndex].episodes[info.episodeIndex]
        val id = (info.card.anilistId + "S${info.seasonIndex}E${info.episodeIndex}").hashCode()

        var title = ep.title
        if (title?.replace(" ", "") == "") {
            title = "Episode " + info.episodeIndex + 1
        }
        var body = ""
        if (type == DownloadType.IsDownloading || type == DownloadType.IsPaused || type == DownloadType.IsFailed) {
            if (!isMovie) {
                body += "S${info.seasonIndex + 1}:E${info.episodeIndex + 1} - ${title}\n"
            }
            body += "$progressPro % (${convertBytesToAny(progress, 1, 2.0)} MB/${
                convertBytesToAny(
                    total,
                    1,
                    2.0
                )
            } MB)"
        }

        val builder = NotificationCompat.Builder(localContext!!, CHANNEL_ID)
            .setSmallIcon(
                when (type) {
                    DownloadType.IsDone -> R.drawable.rddone
                    DownloadType.IsDownloading -> R.drawable.rdload
                    DownloadType.IsPaused -> R.drawable.rdpause
                    DownloadType.IsFailed -> R.drawable.rderror
                    DownloadType.IsStopped -> R.drawable.rderror
                }
            )
            .setContentTitle(
                when (type) {
                    DownloadType.IsDone -> "Download Done"
                    DownloadType.IsDownloading -> "${info.card.title.english} - ${
                        convertBytesToAny(
                            progressPerSec,
                            2,
                            2.0
                        )
                    } MB/s"
                    DownloadType.IsPaused -> "${info.card.title.english} - Paused"
                    DownloadType.IsFailed -> "Download Failed"
                    DownloadType.IsStopped -> "Download Stopped"
                }
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColorized(true)
            .setAutoCancel(true)
            .setColor(localContext!!.getColorFromAttr(R.attr.colorAccent))

        if (type == DownloadType.IsDownloading) {
            builder.setProgress(100, progressPro, false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ep.thumb != null && ep.thumb != "") {
                val bitmap = getImageBitmapFromUrl(ep.thumb)
                if (bitmap != null) {
                    builder.setLargeIcon(bitmap)
                    builder.setStyle(androidx.media.app.NotificationCompat.MediaStyle()) // NICER IMAGE
                }
            }
        }
        if (body.contains("\n") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            println("BIG TEXT: " + body)
            val b = NotificationCompat.BigTextStyle()
            b.bigText(body)
            builder.setStyle(b)
        } else {
            println("SMALL TEXT: " + body)
            builder.setContentText(body)
        }

        if ((type == DownloadType.IsDownloading || type == DownloadType.IsPaused) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val actionTypes: MutableList<DownloadActionType> = ArrayList<DownloadActionType>()
            // INIT
            if (type == DownloadType.IsDownloading) {
                actionTypes.add(DownloadActionType.Pause)
                actionTypes.add(DownloadActionType.Stop)
            }

            if (type == DownloadType.IsPaused) {
                actionTypes.add(DownloadActionType.Resume)
                actionTypes.add(DownloadActionType.Stop)
            }

            // ADD ACTIONS
            for ((index, i) in actionTypes.withIndex()) {
                val _resultIntent = Intent(localContext, DownloadService::class.java)

                _resultIntent.putExtra(
                    "type", when (i) {
                        DownloadActionType.Resume -> "resume"
                        DownloadActionType.Pause -> "pause"
                        DownloadActionType.Stop -> "stop"
                    }
                )

                _resultIntent.putExtra("id", id)

                val pending: PendingIntent = PendingIntent.getService(
                    localContext, 3337 + index + id,
                    _resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(
                    NotificationCompat.Action(
                        when (i) {
                            DownloadActionType.Resume -> R.drawable.rdload
                            DownloadActionType.Pause -> R.drawable.rdpause
                            DownloadActionType.Stop -> R.drawable.rderror
                        }, when (i) {
                            DownloadActionType.Resume -> "Resume"
                            DownloadActionType.Pause -> "Pause"
                            DownloadActionType.Stop -> "Stop"
                        }, pending
                    )
                )
            }
        }

        with(NotificationManagerCompat.from(localContext!!)) {
            // notificationId is a unique int for each notification that you must define
            notify(id, builder.build())
        }
    }
}