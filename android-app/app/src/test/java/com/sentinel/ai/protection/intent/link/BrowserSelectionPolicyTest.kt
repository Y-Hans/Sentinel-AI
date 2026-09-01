package com.sentinel.ai.protection.intent.link

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ActivityInfo
import android.net.Uri
import org.robolectric.RuntimeEnvironment
import com.sentinel.ai.core.preferences.BrowserPreferenceRepository
import com.sentinel.ai.core.browser.BrowserSelectionPolicy
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class BrowserSelectionPolicyTest {

    private lateinit var context: Context
    private lateinit var preferenceRepository: BrowserPreferenceRepository
    private lateinit var policy: BrowserSelectionPolicy

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        preferenceRepository = mockk(relaxed = true)
        policy = BrowserSelectionPolicy(preferenceRepository)
    }

    private fun addBrowserToPackageManager(packageName: String) {
        val pm = shadowOf(context.packageManager)
        
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { 
                this.packageName = packageName 
                name = "$packageName.BrowserActivity"
            }
        }
        
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val intentWithPackage = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE).apply {
            setPackage(packageName)
        }
        
        pm.addResolveInfoForIntent(intent, resolveInfo)
        pm.addResolveInfoForIntent(intentWithPackage, resolveInfo)
    }

    @Test
    fun `when no preference is set, chooser intent is returned`() {
        every { preferenceRepository.getPreferredBrowser() } returns null
        
        addBrowserToPackageManager("com.other.browser")

        val intent = policy.selectBrowserIntent(context, "https://example.com")
        
        assertNotNull(intent)
        assertEquals(Intent.ACTION_CHOOSER, intent?.action)
    }

    @Test
    fun `when preferred browser is set and installed, specific intent is returned`() {
        every { preferenceRepository.getPreferredBrowser() } returns "com.preferred.browser"
        
        addBrowserToPackageManager("com.preferred.browser")

        val intent = policy.selectBrowserIntent(context, "https://example.com")
        
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("com.preferred.browser", intent?.getPackage())
    }

    @Test
    fun `when preferred browser is set but missing, falls back to chooser`() {
        every { preferenceRepository.getPreferredBrowser() } returns "com.missing.browser"
        
        // Only other browser is installed
        addBrowserToPackageManager("com.other.browser")

        val intent = policy.selectBrowserIntent(context, "https://example.com")
        
        assertNotNull(intent)
        assertEquals(Intent.ACTION_CHOOSER, intent?.action)
    }

    @Test
    fun `when preferred browser is disabled, falls back to chooser`() {
        every { preferenceRepository.getPreferredBrowser() } returns "com.disabled.browser"
        addBrowserToPackageManager("com.other.browser")

        val intent = policy.selectBrowserIntent(context, "https://example.com")

        assertEquals(Intent.ACTION_CHOOSER, intent?.action)
    }

    @Test
    fun `when no compatible browsers exist, returns null`() {
        every { preferenceRepository.getPreferredBrowser() } returns null

        assertNull(policy.selectBrowserIntent(context, "https://example.com"))
    }

    @Test
    fun `multiple compatible browsers use chooser`() {
        every { preferenceRepository.getPreferredBrowser() } returns null
        addBrowserToPackageManager("com.first.browser")
        addBrowserToPackageManager("com.second.browser")

        assertEquals(Intent.ACTION_CHOOSER, policy.selectBrowserIntent(context, "https://example.com")?.action)
    }

    @Test
    fun `blank preference behaves as ask every time`() {
        every { preferenceRepository.getPreferredBrowser() } returns "   "
        addBrowserToPackageManager("com.other.browser")

        assertEquals(Intent.ACTION_CHOOSER, policy.selectBrowserIntent(context, "https://example.com")?.action)
    }

    @Test
    fun `invalid URL returns null`() {
        every { preferenceRepository.getPreferredBrowser() } returns null

        assertNull(policy.selectBrowserIntent(context, "not a URL"))
        assertNull(policy.selectBrowserIntent(context, ""))
    }
}
