package jp.muo.oscsync

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented getWiFiGatewayAddress, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under getWiFiGatewayAddress.
        val appContext = InstrumentationRegistry.getTargetContext()
        assertEquals("jp.muo.oscsync", appContext.packageName)
    }
}
