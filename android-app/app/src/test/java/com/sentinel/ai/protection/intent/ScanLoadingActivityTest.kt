package com.sentinel.ai.protection.intent

import android.content.Intent
import com.sentinel.ai.SentinelApp
import com.sentinel.ai.core.browser.BrowserLauncher
import com.sentinel.ai.core.data.ScanRepository
import com.sentinel.ai.core.model.ProtectionDecision
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.ui.components.SecurityTipProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = SentinelApp::class, manifest = Config.NONE, sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class ScanLoadingActivityTest {

    @Test
    fun `green allow automatically invokes BrowserLauncher`() {
        val browserLauncher = launchActivity(result(RiskLevel.GREEN, ProtectionDecision.ALLOW))

        verify(timeout = 5_000, exactly = 1) { browserLauncher.launch("https://example.com") }
    }

    @Test
    fun `yellow does not automatically launch`() = assertNoAutomaticLaunch(RiskLevel.YELLOW, ProtectionDecision.WARN)

    @Test
    fun `red does not automatically launch`() = assertNoAutomaticLaunch(RiskLevel.RED, ProtectionDecision.WARN)

    @Test
    fun `critical does not automatically launch`() = assertNoAutomaticLaunch(RiskLevel.CRITICAL, ProtectionDecision.BLOCK)

    @Test
    fun `warn does not automatically launch`() = assertNoAutomaticLaunch(RiskLevel.GREEN, ProtectionDecision.WARN)

    @Test
    fun `block does not automatically launch`() = assertNoAutomaticLaunch(RiskLevel.CRITICAL, ProtectionDecision.BLOCK)

    @Test
    fun `scan exception does not launch browser`() {
        val repository = mockk<ScanRepository>()
        coEvery { repository.scanLink(any()) } throws IllegalStateException("scan failed")
        val browserLauncher = launchActivity(repository)

        verify(timeout = 5_000, exactly = 0) { browserLauncher.launch(any()) }
    }

    @Test
    fun `missing and invalid payloads do not launch browser`() {
        val missingLauncher = mockk<BrowserLauncher>(relaxed = true)
        launchActivity(Intent(RuntimeEnvironment.getApplication(), ScanLoadingActivity::class.java), missingLauncher)
        verify(exactly = 0) { missingLauncher.launch(any()) }

        val invalidIntent = activityIntent()
            .putExtra(IntentPayloadExtras.EXTRA_PAYLOAD_TYPE, IntentPayloadExtras.TYPE_URL)
            .putExtra(IntentPayloadExtras.EXTRA_PAYLOAD_VALUE, "not a url")
        val invalidLauncher = mockk<BrowserLauncher>(relaxed = true)
        launchActivity(invalidIntent, invalidLauncher)
        verify(exactly = 0) { invalidLauncher.launch(any()) }
    }

    @Test
    fun `browser launcher failure does not crash scan flow`() {
        val browserLauncher = mockk<BrowserLauncher>()
        every { browserLauncher.launch("https://example.com") } returns false

        val activity = launchActivity(result(RiskLevel.GREEN, ProtectionDecision.ALLOW), browserLauncher)

        assertNotNull(activity)
        verify(exactly = 1) { browserLauncher.launch("https://example.com") }
    }

    private fun assertNoAutomaticLaunch(level: RiskLevel, decision: ProtectionDecision) {
        val browserLauncher = launchActivity(result(level, decision))
        verify(timeout = 5_000, exactly = 0) { browserLauncher.launch(any()) }
    }

    private fun launchActivity(result: ScanResult, browserLauncher: BrowserLauncher = mockk(relaxed = true)): BrowserLauncher {
        val repository = mockk<ScanRepository>()
        coEvery { repository.scanLink("https://example.com") } returns result
        launchActivity(activityIntent(), browserLauncher, repository)
        coVerify(timeout = 5_000, exactly = 1) { repository.scanLink("https://example.com") }
        return browserLauncher
    }

    private fun launchActivity(repository: ScanRepository): BrowserLauncher {
        val browserLauncher = mockk<BrowserLauncher>(relaxed = true)
        launchActivity(activityIntent(), browserLauncher, repository)
        coVerify(timeout = 5_000, exactly = 1) { repository.scanLink("https://example.com") }
        return browserLauncher
    }

    private fun launchActivity(intent: Intent, browserLauncher: BrowserLauncher = mockk(relaxed = true), repository: ScanRepository = mockk(relaxed = true)): ScanLoadingActivity {
        val controller = Robolectric.buildActivity(ScanLoadingActivity::class.java, intent)
        val activity = controller.get()
        controller.create()
        activity.scanRepository = repository
        activity.securityTipProvider = object : SecurityTipProvider {
            override fun getRandomTip() = "Test tip"
        }
        activity.browserLauncher = browserLauncher
        controller.start().resume().visible()
        shadowOf(activity.mainLooper).idleFor(1100, TimeUnit.MILLISECONDS)
        return activity
    }

    private fun activityIntent() = Intent(RuntimeEnvironment.getApplication(), ScanLoadingActivity::class.java).apply {
        putExtra(IntentPayloadExtras.EXTRA_PAYLOAD_TYPE, IntentPayloadExtras.TYPE_URL)
        putExtra(IntentPayloadExtras.EXTRA_PAYLOAD_VALUE, "https://example.com")
    }

    private fun result(level: RiskLevel, decision: ProtectionDecision) = ScanResult(
        id = "test",
        source = "test",
        riskLevel = level,
        riskScore = 0f,
        explanation = "test",
        timestamp = 0L,
        decision = decision
    )
}


