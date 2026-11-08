package com.example.ytdl

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

class MainActivity : Activity() {

    private lateinit var urlInput: EditText
    private lateinit var rbAudio: RadioButton
    private lateinit var downloadBtn: TextView
    private lateinit var pasteBtn: TextView
    private lateinit var updateBtn: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var versionText: TextView
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView

    @Volatile private var ready = false
    @Volatile private var busy = true          // true while initializing / updating / downloading
    @Volatile private var pendingAutoStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        rbAudio = findViewById(R.id.rbAudio)
        downloadBtn = findViewById(R.id.downloadBtn)
        pasteBtn = findViewById(R.id.pasteBtn)
        updateBtn = findViewById(R.id.updateBtn)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        versionText = findViewById(R.id.versionText)
        logView = findViewById(R.id.logView)
        scroll = findViewById(R.id.scroll)

        pasteBtn.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!text.isNullOrBlank()) urlInput.setText(text.trim())
        }
        downloadBtn.setOnClickListener { startDownload() }
        updateBtn.setOnClickListener { manualUpdate() }

        if (savedInstanceState == null) handleIntent(intent)
        initLibraries()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // Share (YouTube -> Share -> this app) support
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val link = Regex("https?://\\S+").find(shared)?.value ?: return
        urlInput.setText(link)
        if (ready && !busy) startDownload() else pendingAutoStart = true
    }

    private fun log(msg: String) {
        runOnUiThread {
            logView.append(msg + "\n")
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun setStatus(msg: String) {
        runOnUiThread { statusText.text = msg }
    }

    private fun showVersion() {
        val v = try {
            YoutubeDL.getInstance().versionName(application)
        } catch (e: Exception) {
            null
        }
        runOnUiThread { versionText.text = "yt-dlp: " + (v ?: "bundled version") }
    }

    // Checks for a newer yt-dlp and installs it. Must run on a background thread.
    private fun doUpdate() {
        setStatus("Checking for yt-dlp updates...")
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(application)
            log("yt-dlp update: " + (status?.toString() ?: "no change"))
        } catch (e: Exception) {
            log("Update check failed: ${e.message}")
        }
        showVersion()
    }

    // Called whenever background work finishes
    private fun onIdle() {
        busy = false
        runOnUiThread {
            downloadBtn.isEnabled = true
            setStatus("Ready")
            if (pendingAutoStart && ready) {
                pendingAutoStart = false
                startDownload()
            }
        }
    }

    private fun initLibraries() {
        setStatus("Initializing...")
        Thread {
            try {
                YoutubeDL.getInstance().init(application)
                FFmpeg.getInstance().init(application)
                ready = true
                showVersion()
                doUpdate()          // auto-update yt-dlp on every launch
            } catch (e: Exception) {
                setStatus("Init failed")
                log("Init failed: ${e.message}")
                return@Thread
            }
            onIdle()
        }.start()
    }

    private fun manualUpdate() {
        if (busy || !ready) return
        busy = true
        runOnUiThread { downloadBtn.isEnabled = false }
        Thread {
            doUpdate()
            onIdle()
        }.start()
    }

    private fun startDownload() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty() || !ready || busy) return

        busy = true
        val outDir = File(getExternalFilesDir(null), "Download").apply { mkdirs() }
        downloadBtn.isEnabled = false
        progressBar.progress = 0
        setStatus("Downloading...")
        log("> $url")

        Thread {
            try {
                val request = YoutubeDLRequest(url)
                request.addOption("-o", outDir.absolutePath + "/%(title)s.%(ext)s")
                if (rbAudio.isChecked) {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                } else {
                    request.addOption("-f", "best[ext=mp4]/best")
                }
                YoutubeDL.getInstance().execute(request, null) { progress, _, line ->
                    runOnUiThread { progressBar.progress = progress.toInt().coerceIn(0, 100) }
                    if (line.isNotBlank()) log(line)
                }
                log("Saved in: ${outDir.absolutePath}")
                setStatus("Done")
            } catch (e: Exception) {
                setStatus("Error")
                log("Error: ${e.message}")
            } finally {
                busy = false
                runOnUiThread { downloadBtn.isEnabled = true }
            }
        }.start()
    }
}
