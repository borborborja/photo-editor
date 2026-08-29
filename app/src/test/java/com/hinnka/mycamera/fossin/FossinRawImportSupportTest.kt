package com.hinnka.mycamera.fossin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FossinRawImportSupportTest {
    @Test
    fun `common camera families are recognised without becoming an allow list`() {
        assertEquals(FossinRawFamily.Dng, FossinRawImportSupport.inspect("pixel.dng").family)
        assertEquals(FossinRawFamily.Canon, FossinRawImportSupport.inspect("canon.CR3").family)
        assertEquals(FossinRawFamily.Nikon, FossinRawImportSupport.inspect("nikon.nef").family)
        assertEquals(FossinRawFamily.Sony, FossinRawImportSupport.inspect("sony.arw").family)
        assertEquals(FossinRawFamily.Fujifilm, FossinRawImportSupport.inspect("fuji.raf").family)
        assertEquals(FossinRawFamily.Hasselblad, FossinRawImportSupport.inspect("hassy.3fr").family)
        assertEquals(FossinRawFamily.PhaseOne, FossinRawImportSupport.inspect("phase.iiq").family)
        assertTrue(FossinRawImportSupport.inspect("legacy.qtk").isRawCandidate)
        assertTrue(FossinRawImportSupport.inspect("future-camera.zzraw", "image/x-raw").isRawCandidate)
        assertFalse(FossinRawImportSupport.inspect("photo.jpg", "image/jpeg").isRawCandidate)
    }
}
