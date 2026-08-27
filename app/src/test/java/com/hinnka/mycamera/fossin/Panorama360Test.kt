package com.hinnka.mycamera.fossin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Panorama360Test {
    @Test
    fun parsesGpanoEquirectangularMetadata() {
        val panorama = Panorama360.parseXmp(
            """<rdf:Description GPano:ProjectionType="equirectangular" GPano:FullPanoWidthPixels="8192" GPano:FullPanoHeightPixels="4096" GPano:CroppedAreaImageWidthPixels="8192" GPano:CroppedAreaImageHeightPixels="4096" GPano:CroppedAreaLeftPixels="0" GPano:CroppedAreaTopPixels="0" GPano:InitialViewHeadingDegrees="23.5"/>""",
        )

        requireNotNull(panorama)
        assertEquals(8192, panorama.fullWidth)
        assertEquals(4096, panorama.fullHeight)
        assertEquals(23.5f, panorama.initialHeadingDegrees ?: 0f, 0.0001f)
        assertTrue(panorama.isEquirectangular)
    }

    @Test
    fun rejectsNon360PanoramaMetadata() {
        assertNull(Panorama360.parseXmp("<rdf:Description GPano:ProjectionType=\"cylindrical\"/>"))
        assertNull(Panorama360.manual(3000, 2000))
    }

    @Test
    fun manualPanoramaAndExportMetadataKeepFullSphere() {
        val panorama = requireNotNull(Panorama360.manual(8000, 4000))
        val exported = panorama.forExport(4096, 2048)

        assertEquals(4096, exported.fullWidth)
        assertEquals(2048, exported.fullHeight)
        val xmp = Panorama360.xmp(exported).decodeToString()
        assertTrue(xmp.contains("GPano:ProjectionType=\"equirectangular\""))
        assertTrue(xmp.contains("GPano:FullPanoWidthPixels=\"4096\""))
    }
}
