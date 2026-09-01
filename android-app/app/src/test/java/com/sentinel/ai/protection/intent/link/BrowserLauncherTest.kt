package com.sentinel.ai.protection.intent.link

import android.content.Context
import android.content.Intent
import com.sentinel.ai.core.browser.BrowserLauncher
import com.sentinel.ai.core.browser.BrowserSelectionPolicy
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserLauncherTest {
    private val context = mockk<Context>(relaxed = true)
    private val policy = mockk<BrowserSelectionPolicy>()
    private val launcher = BrowserLauncher(context, policy)

    @Test
    fun `launch delegates selected intent`() {
        val intent = mockk<Intent>()
        every { policy.selectBrowserIntent(context, "https://example.com") } returns intent

        assertTrue(launcher.launch("https://example.com"))
        verify { context.startActivity(intent) }
    }

    @Test
    fun `launch fails safely when policy has no result`() {
        every { policy.selectBrowserIntent(context, "https://example.com") } returns null

        assertFalse(launcher.launch("https://example.com"))
        verify(exactly = 0) { context.startActivity(any()) }
    }
}
