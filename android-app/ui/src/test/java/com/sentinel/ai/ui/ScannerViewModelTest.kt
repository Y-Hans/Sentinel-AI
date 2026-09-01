package com.sentinel.ai.ui

import android.net.Uri
import com.sentinel.ai.core.browser.BrowserLauncher
import com.sentinel.ai.core.browser.BrowserSelectionPolicy
import com.sentinel.ai.core.data.ScanRepository
import com.sentinel.ai.core.model.RiskLevel
import com.sentinel.ai.core.model.ScanResult
import com.sentinel.ai.core.preferences.BrowserPreferenceRepository
import com.sentinel.ai.ui.components.SecurityTipProvider
import com.sentinel.ai.ui.screens.scanner.ScanType
import com.sentinel.ai.ui.screens.scanner.ScannerUiAction
import com.sentinel.ai.ui.screens.scanner.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ScannerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `security tip provider tip is exposed through scanner UI state`() = runTest {
        val tipProvider = object : SecurityTipProvider {
            override fun getRandomTip() = "Use a trusted bookmark."
        }
        val viewModel = ScannerViewModel(
            scanRepository = object : ScanRepository {
                override suspend fun scanLink(link: String) = safeResult()
                override suspend fun scanFile(uri: Uri) = safeResult()
            },
            browserLauncher = BrowserLauncher(
                RuntimeEnvironment.getApplication(),
                BrowserSelectionPolicy(BrowserPreferenceRepository(RuntimeEnvironment.getApplication()))
            ),
            securityTipProvider = tipProvider
        )

        viewModel.onAction(ScannerUiAction.SetScanType(ScanType.LINK))
        viewModel.onAction(ScannerUiAction.UpdateInput("https://example.com"))
        viewModel.onAction(ScannerUiAction.RunScan)
        advanceUntilIdle()

        assertEquals("Use a trusted bookmark.", viewModel.uiState.value.currentTip)
    }

    private fun safeResult() = ScanResult("id", "test", riskLevel = RiskLevel.GREEN, riskScore = 0f, explanation = "safe", timestamp = 0L)
}
