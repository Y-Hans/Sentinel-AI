package com.sentinel.ai.core.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BrowserPreferenceRepositoryTest {
    private lateinit var repository: BrowserPreferenceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("sentinel_browser_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        repository = BrowserPreferenceRepository(context)
    }

    @Test
    fun `ask every time is persisted as null preference`() {
        repository.setPreferredBrowser(null)
        assertEquals(null, repository.getPreferredBrowser())
        assertEquals(BrowserPreferenceRepository.ASK_EVERY_TIME, repository.getRawPreference())
    }

    @Test
    fun `package preference round trips`() {
        repository.setPreferredBrowser("com.example.browser")
        assertEquals("com.example.browser", repository.getPreferredBrowser())
        assertEquals("com.example.browser", repository.getRawPreference())
    }
}
