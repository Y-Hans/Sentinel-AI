package com.sentinel.ai.warning

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.sentinel.ai.R
import com.sentinel.ai.core.model.ScanResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CriticalAlertActivity : ComponentActivity() {

    private var alarmPlayer: MediaPlayer? = null
    private var countdownJob: Job? = null
    private var canExit = false

    private lateinit var countdownText: TextView
    private lateinit var goBackButton: Button
    private lateinit var viewDetailsButton: Button

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (canExit) {
                stopDeviceAlert()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url")
        val score = intent.getIntExtra("score", 0)
        val decision = intent.getStringExtra("decision")
        configureFullScreen()
        setContentView(R.layout.activity_critical_alert)
        onBackPressedDispatcher.addCallback(this, backCallback)

        val result = intent.toScanResultOrNull() ?: return finish()
        presentAlert(result)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val result = intent.toScanResultOrNull() ?: return
        presentAlert(result)
    }

    private fun presentAlert(result: ScanResult) {
        countdownJob?.cancel()
        stopDeviceAlert()
        canExit = false
        backCallback.isEnabled = true
        bindViews(result)
        startDeviceAlert()
        startCountdown()
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        stopDeviceAlert()
        super.onDestroy()
    }

    private fun configureFullScreen() {
        window.setBackgroundDrawable(ColorDrawable(Color.RED))
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        WindowInsetsControllerCompat(window, window.decorView).systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun bindViews(result: ScanResult) {
        val scoreText = findViewById<TextView>(R.id.criticalScoreText)
        val reasonsText = findViewById<TextView>(R.id.criticalReasonsText)
        countdownText = findViewById(R.id.criticalCountdownText)
        goBackButton = findViewById(R.id.criticalGoBackButton)
        viewDetailsButton = findViewById(R.id.criticalViewDetailsButton)

        scoreText.text = getString(R.string.critical_alert_score, result.riskScore.toInt())
        reasonsText.text = result.toWarningUiModel().reasons
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n") { "* $it" }
            ?: getString(R.string.critical_alert_no_reasons)

        goBackButton.isEnabled = false
        viewDetailsButton.isEnabled = false
        goBackButton.setOnClickListener {
            stopDeviceAlert()
            finish()
        }
        viewDetailsButton.setOnClickListener {
            stopDeviceAlert()
            startActivity(ScamWarningActivity.newIntent(this, result))
            finish()
        }
    }

    private fun startCountdown() {
        countdownJob = lifecycleScope.launch {
            for (remainingSeconds in COUNTDOWN_SECONDS downTo 0) {
                countdownText.text = getString(R.string.critical_alert_countdown, remainingSeconds)
                if (remainingSeconds > 0) {
                    delay(ONE_SECOND_MS)
                }
            }
            canExit = true
            backCallback.isEnabled = false
            goBackButton.isEnabled = true
            viewDetailsButton.isEnabled = true
            stopDeviceAlert()
        }
    }

    private fun startDeviceAlert() {
        startAlarmSound()
        startVibration()
    }

    private fun startAlarmSound() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        alarmPlayer = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@CriticalAlertActivity, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    private fun startVibration() {
        val vibrator = getVibrator()
        val pattern = longArrayOf(0L, 600L, 250L, 600L, 500L)
        val effect = VibrationEffect.createWaveform(pattern, 0)
        vibrator.vibrate(effect)
    }

    private fun stopDeviceAlert() {
        alarmPlayer?.run {
            runCatching {
                if (isPlaying) stop()
                release()
            }
        }
        alarmPlayer = null
        getVibrator().cancel()
    }

    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
    }

    companion object {
        private const val AUTO_DISMISS_SECONDS = 3
        private const val COUNTDOWN_SECONDS = AUTO_DISMISS_SECONDS
        private const val ONE_SECOND_MS = 1_000L

        fun newIntent(context: Context, result: ScanResult): Intent =
            Intent(context, CriticalAlertActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                .putExtra(EXTRA_ID, result.id)
                .putExtra(EXTRA_SOURCE, result.source)
                .putExtra(EXTRA_RISK_LEVEL, result.riskLevel.name)
                .putExtra(EXTRA_RISK_SCORE, result.riskScore)
                .putExtra(EXTRA_EXPLANATION, result.explanation)
                .putExtra(EXTRA_TIMESTAMP, result.timestamp)
    }
}

private fun Intent.toScanResultOrNull(): ScanResult? {
    val id = getStringExtra(EXTRA_ID) ?: return null
    val source = getStringExtra(EXTRA_SOURCE) ?: return null
    val riskLevelName = getStringExtra(EXTRA_RISK_LEVEL) ?: return null
    val riskScore = getFloatExtra(EXTRA_RISK_SCORE, 0f)
    val explanation = getStringExtra(EXTRA_EXPLANATION).orEmpty()
    val timestamp = getLongExtra(EXTRA_TIMESTAMP, 0L)

    return ScanResult(
        id = id,
        source = source,
        riskLevel = runCatching { com.sentinel.ai.core.model.RiskLevel.valueOf(riskLevelName) }.getOrNull()
            ?: return null,
        riskScore = riskScore,
        explanation = explanation,
        timestamp = timestamp
    )
}

private const val EXTRA_ID = "extra_id"
private const val EXTRA_SOURCE = "extra_source"
private const val EXTRA_RISK_LEVEL = "extra_risk_level"
private const val EXTRA_RISK_SCORE = "extra_risk_score"
private const val EXTRA_EXPLANATION = "extra_explanation"
private const val EXTRA_TIMESTAMP = "extra_timestamp"
