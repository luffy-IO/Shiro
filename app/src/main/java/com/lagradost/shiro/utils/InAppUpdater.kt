package com.lagradost.shiro.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.shiro.BuildConfig
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.tv.TvActivity.Companion.tvActivity
import java.io.*
import java.net.URL
import java.net.URLConnection
import kotlin.concurrent.thread

// Stolen from LagradOst's quicknovel :)
object InAppUpdater {
    // === IN APP UPDATER ===
    data class GithubAsset(
        @JsonProperty("name") val name: String,
        @JsonProperty("size") val size: Int, // Size bytes
        @JsonProperty("browser_download_url") val browser_download_url: String, // download link
        @JsonProperty("content_type") val content_type: String, // application/vnd.android.package-archive
    )

    data class GithubRelease(
        @JsonProperty("tag_name") val tag_name: String, // Version code
        @JsonProperty("body") val body: String, // Desc
        @JsonProperty("assets") val assets: List<GithubAsset>,
        @JsonProperty("target_commitish") val target_commitish: String, // branch
        @JsonProperty("draft") val draft: Boolean,
        @JsonProperty("prerelease") val prerelease: Boolean,
    )

    data class Update(
        @JsonProperty("shouldUpdate") val shouldUpdate: Boolean,
        @JsonProperty("updateURL") val updateURL: String?,
        @JsonProperty("updateVersion") val updateVersion: String?,
        @JsonProperty("changelog") val changelog: String?,
    )

    private val mapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()


    fun FragmentActivity.getAppUpdate(): Update {
        try {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

            val isBetaMode = settingsManager.getBoolean("beta_mode", false)
            val isTv = tvActivity != null

            val url = "https://api.github.com/repos/Blatzar/shiro-app/releases"
            val headers = mapOf("Accept" to "application/vnd.github.v3+json")
            val response =
                mapper.readValue<List<GithubRelease>>(khttp.get(url, headers = headers).text)

            val cleanedResponse = response.filter { (!it.prerelease || isBetaMode) && !it.draft}

            val versionRegex = if (isTv) {
                Regex("""(.*?((\d)\.(\d)\.(\d))-TV\.apk)""")
            } else {
                Regex("""(.*?((\d)\.(\d)\.(\d))\.apk)""")
            }

            /*
            val releases = response.map { it.assets }.flatten()
                .filter { it.content_type == "application/vnd.android.package-archive" }
            val found =
                releases.sortedWith(compareBy {
                    versionRegex.find(it.name)?.groupValues?.get(2)
                }).toList().lastOrNull()*/
            val found =
                cleanedResponse.sortedWith(compareBy { release ->
                    release.assets.filter { it.content_type == "application/vnd.android.package-archive" }
                        .getOrNull(0)?.name?.let { it1 ->
                            versionRegex.find(
                                it1
                            )?.groupValues?.get(2)
                        }
                }).toList().lastOrNull()
            val foundAsset = found?.assets?.getOrNull(0)
            val currentVersion = this.packageName?.let {
                this.packageManager.getPackageInfo(
                    it,
                    0
                )
            }

            val foundVersion = foundAsset?.name?.let { versionRegex.find(it) }
            val shouldUpdate =
                if (found != null && foundAsset?.browser_download_url != "" && foundVersion != null) currentVersion?.versionName?.compareTo(
                    foundVersion.groupValues[2]
                )!! < 0 else false
            return if (foundVersion != null) {
                Update(shouldUpdate, foundAsset.browser_download_url, foundVersion.groupValues[2], found.body)
            } else {
                Update(false, null, null, null)
            }

        } catch (e: Exception) {
            println(e)
            return Update(false, null, null, null)
        }
    }

    fun FragmentActivity.downloadUpdate(url: String, localContext: Context): Boolean {
        println("DOWNLOAD UPDATE $url")
        var fullResume = false // IF FULL RESUME
        try {
            // =================== DOWNLOAD POSTERS AND SETUP PATH ===================
            val path = this.filesDir.toString() +
                    "/Download/apk/update.apk"

            // =================== MAKE DIRS ===================
            val rFile = File(path)
            try {
                rFile.parentFile.mkdirs()
            } catch (_ex: Exception) {
                println("FAILED:::$_ex")
            }
            val url = url.replace(" ", "%20")

            val _url = URL(url)

            val connection: URLConnection = _url.openConnection()

            var bytesRead = 0L

            // =================== STORAGE ===================
            try {
                if (!rFile.exists()) {
                    rFile.createNewFile()
                } else {
                    rFile.delete()
                    rFile.createNewFile()
                }
            } catch (e: Exception) {
                println(e)
                this.runOnUiThread {
                    Toast.makeText(localContext, "Permission error when downloading update", Toast.LENGTH_SHORT).show()
                }
                return false
            }

            // =================== CONNECTION ===================
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.connectTimeout = 10000
            var clen = 0
            try {
                connection.connect()
                clen = connection.contentLength
                println("CONTENTN LENGTH: $clen")
            } catch (_ex: Exception) {
                println("CONNECT:::$_ex")
                _ex.printStackTrace()
            }

            // =================== VALIDATE ===================
            if (clen < 5000000) { // min of 5 MB
                clen = 0
            }
            if (clen <= 0) { // TO SMALL OR INVALID
                //showNot(0, 0, 0, DownloadType.IsFailed, info)
                return false
            }

            // =================== SETUP VARIABLES ===================
            //val bytesTotal: Long = (clen + bytesRead.toInt()).toLong()
            val input: InputStream = BufferedInputStream(connection.inputStream)
            val output: OutputStream = FileOutputStream(rFile, false)
            var bytesPerSec = 0L
            val buffer = ByteArray(1024)
            var count: Int
            //var lastUpdate = System.currentTimeMillis()

            while (true) {
                try {
                    count = input.read(buffer)
                    if (count < 0) break

                    bytesRead += count
                    bytesPerSec += count
                    output.write(buffer, 0, count)
                } catch (_ex: Exception) {
                    println("CONNECT TRUE:::$_ex")
                    _ex.printStackTrace()
                    fullResume = true
                    break
                }
            }

            if (fullResume) { // IF FULL RESUME DELETE CURRENT AND DONT SHOW DONE
                with(NotificationManagerCompat.from(localContext)) {
                    cancel(-1)
                }
            }

            output.flush()
            output.close()
            input.close()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val contentUri = FileProvider.getUriForFile(
                    localContext,
                    BuildConfig.APPLICATION_ID + ".provider",
                    rFile
                )
                val install = Intent(Intent.ACTION_VIEW)
                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                install.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                install.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                install.data = contentUri
                this.startActivity(install)
                return true
            } else {
                val apkUri = Uri.fromFile(rFile)
                val install = Intent(Intent.ACTION_VIEW)
                install.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                install.setDataAndType(
                    apkUri,
                    "application/vnd.android.package-archive"
                )
                this.startActivity(install)
                return true
            }

        } catch (_ex: Exception) {
            println("FATAL EX DOWNLOADING:::$_ex")
            return false
        }
    }

    fun FragmentActivity.runAutoUpdate(localContext: Context, checkAutoUpdate: Boolean = true): Boolean {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        if (!checkAutoUpdate || settingsManager.getBoolean("auto_update", true)
        ) {
            val update = getAppUpdate()
            if (update.shouldUpdate && update.updateURL != null) {
                this.runOnUiThread {
                    val currentVersion = this.packageName?.let {
                        this.packageManager.getPackageInfo(
                            it,
                            0
                        )
                    }

                    val builder: AlertDialog.Builder = AlertDialog.Builder(localContext, R.style.AlertDialogCustom)
                    builder.setTitle("New update found!\n${currentVersion?.versionName} -> ${update.updateVersion}")
                    builder.setMessage("${update.changelog}")

                    builder.apply {
                        setPositiveButton("Update") { _, _ ->
                            Toast.makeText(this@runAutoUpdate, "Download started", Toast.LENGTH_LONG).show()
                            thread {
                                val downloadStatus = downloadUpdate(update.updateURL, localContext)
                                if (!downloadStatus) {
                                    runOnUiThread {
                                        Toast.makeText(
                                            localContext,
                                            "Download Failed",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } /*else {
                                        activity.runOnUiThread {
                                            Toast.makeText(localContext,
                                                "Downloaded APK",
                                                Toast.LENGTH_LONG).show()
                                        }
                                    }*/
                            }
                        }

                        setNegativeButton("Cancel") { _, _ -> }

                        if (checkAutoUpdate) {
                            setNeutralButton("Don't show again") { _, _ ->
                                settingsManager.edit().putBoolean("auto_update", false).apply()
                            }
                        }
                    }
                    builder.show()
                }
                return true
            }
            return false
        }
        return false
    }
}
