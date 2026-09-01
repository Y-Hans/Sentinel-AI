package com.sentinel.ai.ui.protection

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ProtectionControlTest {

    @Test
    fun `sync handles background service start exception safely without throwing`() {
        val baseContext = RuntimeEnvironment.getApplication()
        val throwingContext = object : ContextWrapper(baseContext) {
            override fun startService(service: Intent?): android.content.ComponentName? {
                throw IllegalStateException("Not allowed to start service Intent: app is in background")
            }
        }

        // Must not throw exception
        ProtectionControl.sync(throwingContext)
        assertNotNull(ProtectionControl)
    }

    @Test
    fun `sync handles background service stop exception safely without throwing`() {
        val baseContext = RuntimeEnvironment.getApplication()
        val throwingContext = object : ContextWrapper(baseContext) {
            override fun stopService(name: Intent?): Boolean {
                throw SecurityException("Unable to stop service: permission denied")
            }
        }

        ProtectionControl.setProtectionEnabled(throwingContext, false)
        // Must not throw exception
        ProtectionControl.sync(throwingContext)
        assertNotNull(ProtectionControl)
    }
}
