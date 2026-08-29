package com.hinnka.mycamera.fossin

/**
 * A deliberately permissive description of commonly encountered camera RAW containers.
 *
 * This is an import hint, never an allow-list. The editor still gives the bundled LibRaw
 * decoder the final word, including for cameras released after this app. The hint exists so
 * known RAW files are developed from sensor data before Android's bitmap decoders get a chance
 * to return a small embedded JPEG preview.
 */
internal enum class FossinRawFamily {
    Dng,
    Canon,
    Nikon,
    Sony,
    Fujifilm,
    Olympus,
    Panasonic,
    Leica,
    Pentax,
    Sigma,
    Hasselblad,
    PhaseOne,
    Other,
    Raster,
}

internal data class FossinRawImportHint(
    val family: FossinRawFamily,
    val extension: String?,
    val isRawCandidate: Boolean,
)

internal object FossinRawImportSupport {
    private val familiesByExtension = mapOf(
        "dng" to FossinRawFamily.Dng,
        "cr2" to FossinRawFamily.Canon,
        "cr3" to FossinRawFamily.Canon,
        "crw" to FossinRawFamily.Canon,
        "nef" to FossinRawFamily.Nikon,
        "nrw" to FossinRawFamily.Nikon,
        "arw" to FossinRawFamily.Sony,
        "srf" to FossinRawFamily.Sony,
        "sr2" to FossinRawFamily.Sony,
        "raf" to FossinRawFamily.Fujifilm,
        "orf" to FossinRawFamily.Olympus,
        "rw2" to FossinRawFamily.Panasonic,
        "rwl" to FossinRawFamily.Leica,
        "pef" to FossinRawFamily.Pentax,
        "ptx" to FossinRawFamily.Pentax,
        "x3f" to FossinRawFamily.Sigma,
        "3fr" to FossinRawFamily.Hasselblad,
        "fff" to FossinRawFamily.Hasselblad,
        "iiq" to FossinRawFamily.PhaseOne,
        "cap" to FossinRawFamily.Other,
        "bay" to FossinRawFamily.Other,
        "bmq" to FossinRawFamily.Other,
        "cine" to FossinRawFamily.Other,
        "ci" to FossinRawFamily.Other,
        "crx" to FossinRawFamily.Other,
        "cs1" to FossinRawFamily.Other,
        "dc2" to FossinRawFamily.Other,
        "dcr" to FossinRawFamily.Other,
        "eip" to FossinRawFamily.Other,
        "erf" to FossinRawFamily.Other,
        "gpr" to FossinRawFamily.Other,
        "ia" to FossinRawFamily.Other,
        "k25" to FossinRawFamily.Other,
        "kdc" to FossinRawFamily.Other,
        "mef" to FossinRawFamily.Other,
        "mdc" to FossinRawFamily.Other,
        "mos" to FossinRawFamily.Other,
        "mrw" to FossinRawFamily.Other,
        "pxn" to FossinRawFamily.Other,
        "qtk" to FossinRawFamily.Other,
        "r3d" to FossinRawFamily.Other,
        "raw" to FossinRawFamily.Other,
        "rdc" to FossinRawFamily.Other,
        "rwz" to FossinRawFamily.Other,
        "srw" to FossinRawFamily.Other,
    )

    fun inspect(displayName: String?, mimeType: String? = null): FossinRawImportHint {
        val extension = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        val family = extension?.let(familiesByExtension::get)
        val rawMime = mimeType.orEmpty().lowercase().let { mime ->
            mime.contains("raw") || mime.contains("x-adobe-dng") || mime.contains("x-canon-cr2")
        }
        return FossinRawImportHint(
            family = family ?: if (rawMime) FossinRawFamily.Other else FossinRawFamily.Raster,
            extension = extension,
            isRawCandidate = family != null || rawMime,
        )
    }
}
