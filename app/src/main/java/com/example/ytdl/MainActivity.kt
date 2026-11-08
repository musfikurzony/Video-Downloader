package com.example.ytdl

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
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
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView

    @Volatile
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        rbAudio = findViewById(R.id.rbAudio)
        downloadBtn = findViewById(R.id.downloadBtn)
        pasteBtn = findViewById(R.id.pasteBtn)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        logView = findViewById(R.id.logView)
        scroll = findViewById(R.id.scroll)

        pasteBtn.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!text.isNullOrBlank()) urlInput.setText(text.trim())
        }
        downloadBtn.setOnClickListener { startDownload() }

        initLibraries()
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

    private fun initLibraries() {
        setStatus("Initializing...")
        Thread {
            try {
                YoutubeDL.getInstance().init(application)
                FFmpeg.getInstance().init(application)
                ready = true
                runOnUiThread { downloadBtn.isEnabled = true }
                setStatus("Ready")
            } catch (e: Exception) {
                setStatus("Init failed")
                log("Init failed: ${e.message}")
            }
        }.start()
    }

    private fun startDownload() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty() || !ready || !downloadBtn.isEnabled) return

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
                setStatus("Done")
                log("Saved in: ${outDir.absolutePath}")
            } catch (e: Exception) {
                setStatus("Error")
                log("Error: ${e.message}")
            } finally {
                runOnUiThread { downloadBtn.isEnabled = true }
            }
        }.start()
    }
}
