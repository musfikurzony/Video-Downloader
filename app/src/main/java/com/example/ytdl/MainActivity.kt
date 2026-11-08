package com.example.ytdl

import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var audioOnly: CheckBox
    private lateinit var downloadBtn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView

    @Volatile
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        initLibraries()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "YT-DLP Downloader"
            textSize = 22f
        })

        urlInput = EditText(this).apply {
            hint = "ভিডিওর লিংক পেস্ট করো"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        root.addView(urlInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        audioOnly = CheckBox(this).apply { text = "শুধু অডিও (MP3)" }
        root.addView(audioOnly)

        downloadBtn = Button(this).apply {
            text = "Download"
            isEnabled = false
            setOnClickListener { startDownload() }
        }
        root.addView(downloadBtn)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        root.addView(progressBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        logView = TextView(this).apply { textSize = 12f }
        scroll = ScrollView(this).apply { addView(logView) }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun log(msg: String) {
        runOnUiThread {
            logView.append(msg + "\n")
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun initLibraries() {
        log("Initializing...")
        Thread {
            try {
                YoutubeDL.getInstance().init(application)
                FFmpeg.getInstance().init(application)
                ready = true
                runOnUiThread { downloadBtn.isEnabled = true }
                log("Ready.")
            } catch (e: Exception) {
                log("Init failed: ${e.message}")
            }
        }.start()
    }

    private fun startDownload() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty() || !ready) return

        val outDir = File(getExternalFilesDir(null), "Download").apply { mkdirs() }
        downloadBtn.isEnabled = false
        progressBar.progress = 0
        log("Downloading: $url")

        Thread {
            try {
                val request = YoutubeDLRequest(url)
                request.addOption("-o", outDir.absolutePath + "/%(title)s.%(ext)s")
                if (audioOnly.isChecked) {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                } else {
                    request.addOption("-f", "best[ext=mp4]/best")
                }
                YoutubeDL.getInstance().execute(request, null) { progress, _, line ->
                    runOnUiThread { progressBar.progress = progress.toInt().coerceIn(0, 100) }
                    if (line.isNotBlank()) log(line)
                }
                log("Done! Saved in:\n${outDir.absolutePath}")
            } catch (e: Exception) {
                log("Error: ${e.message}")
            } finally {
                runOnUiThread { downloadBtn.isEnabled = true }
            }
        }.start()
    }
}
