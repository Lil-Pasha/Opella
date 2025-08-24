package com.example.secondwork

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val STORAGE_PERMISSION_CODE = 1001
        const val FILE_PROVIDER_AUTHORITY = "com.example.secondwork.fileprovider"
    }

    private val repoOwner = "Lil-Pasha"
    private val repoName = "Opella"

    private var apkDownloadUrl = ""
    private var downloadId: Long = -1
    private lateinit var downloadManager: DownloadManager

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            if (id == downloadId) {
                installApk()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val sectionUpdates = findViewById<TextView>(R.id.sectionUpdates)
        val textUpdates = findViewById<TextView>(R.id.textUpdates)

        sectionUpdates.setOnClickListener {
            textUpdates.text = "Checking for updates..."
            checkForUpdates(textUpdates)
        }

        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val textUpdates = findViewById<TextView>(R.id.textUpdates)
        // You can disable auto update check on resume if you want
        // checkForUpdates(textUpdates)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(downloadReceiver)
    }

    private fun getAppVersionName(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun checkForUpdates(statusView: TextView) {
        val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    statusView.text = "Network error: ${e.message}"
                    Toast.makeText(
                        this@SettingsActivity,
                        "Check your connection",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread {
                        statusView.text = "Server error: ${response.code}"
                    }
                    return
                }

                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    runOnUiThread {
                        statusView.text = "Empty response from GitHub"
                    }
                    return
                }

                try {
                    val json = JSONObject(body)
                    val latestVersionTag = json.getString("tag_name")
                    val assets = json.getJSONArray("assets")
                    var apkFound = false

                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.getString("name")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.getString("browser_download_url")
                            apkFound = true
                            break
                        }
                    }

                    if (!apkFound) {
                        runOnUiThread {
                            statusView.text = "No APK found in the release"
                        }
                        return
                    }

                    val latestVersion = latestVersionTag.removePrefix("v")
                    val currentVersion = getAppVersionName()

                    runOnUiThread {
                        if (isNewVersionAvailable(currentVersion, latestVersion)) {
                            statusView.text = "New version found: $latestVersion. Downloading..."
                            requestStoragePermission()
                        } else {
                            statusView.text = "You have the latest version: $currentVersion"
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        statusView.text = "Error parsing data: ${e.message}"
                    }
                }
            }
        })
    }

    private fun isNewVersionAvailable(current: String, latest: String): Boolean {
        fun parseVersion(version: String): List<Int> =
            version.split('.').map { it.toIntOrNull() ?: 0 }

        val currentParts = parseVersion(current)
        val latestParts = parseVersion(latest)

        for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
            val cur = currentParts.getOrElse(i) { 0 }
            val lat = latestParts.getOrElse(i) { 0 }
            when {
                lat > cur -> return true
                lat < cur -> return false
            }
        }
        return false
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { // before Android 10
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
            } else {
                downloadApk()
            }
        } else {
            downloadApk()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                downloadApk()
            } else {
                Toast.makeText(
                    this,
                    "Storage permission is required for update",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun downloadApk() {
        if (apkDownloadUrl.isEmpty()) return

        val fileName = "Opella_${System.currentTimeMillis()}.apk"
        val request = DownloadManager.Request(Uri.parse(apkDownloadUrl))
            .setTitle("Opella update")
            .setDescription("Downloading new version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        downloadId = downloadManager.enqueue(request)
    }

    private fun installApk() {
        // Path to the downloaded file from DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (uriIndex == -1) {
                Toast.makeText(this, "Failed to get file path", Toast.LENGTH_SHORT).show()
                cursor.close()
                return
            }
            val uriString = cursor.getString(uriIndex)

            val fileUri = Uri.parse(uriString)
            val file = File(fileUri.path ?: "")
            if (!file.exists()) {
                Toast.makeText(this, "Installation file not found", Toast.LENGTH_SHORT).show()
                return
            }

            // Create Uri using FileProvider
            val apkUri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, file)

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            startActivity(intent)
        } else {
            Toast.makeText(this, "Error trying to install APK", Toast.LENGTH_SHORT).show()
        }
    }
}
