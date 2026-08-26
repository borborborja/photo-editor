package com.hinnka.mycamera.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun `recognises a newer patch version`() {
        assertTrue(isNewerVersion(candidate = "1.28.4", current = "1.28.3"))
    }

    @Test
    fun `recognises a newer minor version`() {
        assertTrue(isNewerVersion(candidate = "1.29.0", current = "1.28.99"))
    }

    @Test
    fun `does not offer the installed or an older version`() {
        assertFalse(isNewerVersion(candidate = "1.28.3", current = "1.28.3"))
        assertFalse(isNewerVersion(candidate = "1.28.2", current = "1.28.3"))
    }
}
