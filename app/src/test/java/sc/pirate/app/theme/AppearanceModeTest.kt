package sc.pirate.app.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceModeTest {
    @Test
    fun `storage values round trip and unknown values use system`() {
        AppearanceMode.entries.forEach { mode ->
            assertEquals(mode, AppearanceMode.fromStorage(mode.storageValue.uppercase()))
        }
        assertEquals(AppearanceMode.System, AppearanceMode.fromStorage(null))
        assertEquals(AppearanceMode.System, AppearanceMode.fromStorage("future-mode"))
    }

    @Test
    fun `theme resolution honors explicit choices`() {
        assertTrue(AppearanceMode.System.usesDarkTheme(systemInDarkTheme = true))
        assertFalse(AppearanceMode.System.usesDarkTheme(systemInDarkTheme = false))
        assertFalse(AppearanceMode.Light.usesDarkTheme(systemInDarkTheme = true))
        assertTrue(AppearanceMode.Dark.usesDarkTheme(systemInDarkTheme = false))
    }
}
