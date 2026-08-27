package com.hinnka.mycamera.fossin

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.exifinterface.media.ExifInterface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.charset.StandardCharsets
import kotlin.math.max

/** GPano description retained independently of the immutable source bitmap. */
internal data class Panorama360Metadata(
    val fullWidth: Int,
    val fullHeight: Int,
    val croppedWidth: Int = fullWidth,
    val croppedHeight: Int = fullHeight,
    val croppedLeft: Int = 0,
    val croppedTop: Int = 0,
    val initialHeadingDegrees: Float? = null,
) {
    val isEquirectangular: Boolean get() = fullWidth > 0 && fullHeight > 0 && fullWidth >= fullHeight * 2

    fun forExport(width: Int, height: Int): Panorama360Metadata = copy(
        fullWidth = width,
        fullHeight = height,
        croppedWidth = width,
        croppedHeight = height,
        croppedLeft = 0,
        croppedTop = 0,
    )
}

internal object Panorama360 {
    private const val MAX_XMP_SCAN_BYTES = 512 * 1024
    private val attribute = Regex("""(?:GPano:)?([A-Za-z]+)\s*=\s*[\"']([^\"']+)[\"']""")

    suspend fun detect(context: Context, uri: android.net.Uri, width: Int, height: Int): Panorama360Metadata? =
        withContext(Dispatchers.IO) {
            val xmp = context.contentResolver.openInputStream(uri)?.use(::readXmpPrefix) ?: return@withContext null
            parseXmp(xmp) ?: return@withContext null
        }

    /** A 2:1 raster is not automatically treated as 360: users opt in to avoid false positives. */
    fun manual(width: Int, height: Int): Panorama360Metadata? =
        if (width > 0 && height > 0 && width.toFloat() / height >= 1.95f) Panorama360Metadata(width, height) else null

    fun parseXmp(xmp: String): Panorama360Metadata? {
        if (!xmp.contains("ProjectionType", ignoreCase = true) ||
            !Regex("""ProjectionType\s*=\s*[\"']equirectangular[\"']""", RegexOption.IGNORE_CASE).containsMatchIn(xmp)
        ) return null
        val values = attribute.findAll(xmp).associate { match -> match.groupValues[1] to match.groupValues[2] }
        fun number(name: String): Int? = values[name]?.toIntOrNull()
        val fullWidth = number("FullPanoWidthPixels") ?: return null
        val fullHeight = number("FullPanoHeightPixels") ?: return null
        return Panorama360Metadata(
            fullWidth = fullWidth,
            fullHeight = fullHeight,
            croppedWidth = number("CroppedAreaImageWidthPixels") ?: fullWidth,
            croppedHeight = number("CroppedAreaImageHeightPixels") ?: fullHeight,
            croppedLeft = number("CroppedAreaLeftPixels") ?: 0,
            croppedTop = number("CroppedAreaTopPixels") ?: 0,
            initialHeadingDegrees = values["InitialViewHeadingDegrees"]?.toFloatOrNull(),
        )
    }

    fun xmp(metadata: Panorama360Metadata): ByteArray = """
        <x:xmpmeta xmlns:x="adobe:ns:meta/">
          <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description xmlns:GPano="http://ns.google.com/photos/1.0/panorama/"
              GPano:ProjectionType="equirectangular"
              GPano:UsePanoramaViewer="True"
              GPano:FullPanoWidthPixels="${metadata.fullWidth}"
              GPano:FullPanoHeightPixels="${metadata.fullHeight}"
              GPano:CroppedAreaImageWidthPixels="${metadata.croppedWidth}"
              GPano:CroppedAreaImageHeightPixels="${metadata.croppedHeight}"
              GPano:CroppedAreaLeftPixels="${metadata.croppedLeft}"
              GPano:CroppedAreaTopPixels="${metadata.croppedTop}"${metadata.initialHeadingDegrees?.let { "\n              GPano:InitialViewHeadingDegrees=\"$it\"" } ?: ""}/>
          </rdf:RDF>
        </x:xmpmeta>
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)

    /** JPEG compression does not preserve XMP; insert the GPano APP1 segment explicitly. */
    suspend fun writeJpeg(
        context: Context,
        bitmap: Bitmap,
        output: OutputStream,
        quality: Int,
        metadata: Panorama360Metadata?,
        sourceUri: android.net.Uri? = null,
        metadataPolicy: PhotoEditorExportMetadata = PhotoEditorExportMetadata.Preserve,
    ): Boolean = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "fossin-export").apply { mkdirs() }
        val temporary = File(directory, "${System.nanoTime()}.jpg")
        try {
            if (!FileOutputStream(temporary).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(80, 100), it) }) {
                return@withContext false
            }
            copyExif(context, sourceUri, temporary, metadataPolicy)
            FileInputStream(temporary).use { input ->
                if (metadata == null) input.copyTo(output)
                else injectXmp(input, output, xmp(metadata))
            }
            true
        } finally {
            temporary.delete()
        }
    }

    private fun readXmpPrefix(input: InputStream): String {
        val bytes = input.readNBytes(MAX_XMP_SCAN_BYTES)
        return bytes.toString(StandardCharsets.UTF_8)
    }

    private fun injectXmp(input: InputStream, output: OutputStream, xmp: ByteArray) {
        val image = input.readBytes()
        require(image.size >= 2 && image[0].toInt() == 0xff && image[1].toInt() == 0xd8) { "Not a JPEG" }
        val namespace = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(StandardCharsets.UTF_8)
        val size = namespace.size + xmp.size + 2
        require(size <= 0xffff) { "GPano XMP is too large" }
        output.write(image, 0, 2)
        output.write(0xff)
        output.write(0xe1)
        output.write(size shr 8)
        output.write(size and 0xff)
        output.write(namespace)
        output.write(xmp)
        output.write(image, 2, image.size - 2)
    }

    /** Copies safe capture fields before GPano XMP is inserted; all output remains freshly encoded. */
    private fun copyExif(
        context: Context,
        sourceUri: android.net.Uri?,
        target: File,
        policy: PhotoEditorExportMetadata,
    ) {
        if (sourceUri == null || policy == PhotoEditorExportMetadata.Minimal) return
        runCatching {
            val source = context.contentResolver.openInputStream(sourceUri)?.use(::ExifInterface) ?: return
            val targetExif = ExifInterface(target.absolutePath)
            exifTags.forEach { tag ->
                if (policy == PhotoEditorExportMetadata.RemoveLocation && tag in locationTags) return@forEach
                source.getAttribute(tag)?.let { targetExif.setAttribute(tag, it) }
            }
            targetExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            targetExif.saveAttributes()
        }
    }

    private val exifTags = listOf(
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_PROCESSING_METHOD,
    )
    private val locationTags = exifTags.filter { it.startsWith("GPS") }.toSet()
}

/** OpenGL ES equirectangular preview; it is local, touch-operated and needs no Google library. */
@Composable
internal fun Panorama360Viewer(bitmap: Bitmap, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = remember { PanoramaGlSurfaceView(context) }
    DisposableEffect(view) {
        view.onResume()
        onDispose { view.onPause() }
    }
    AndroidView(
        factory = { view.apply { setBitmap(bitmap) } },
        update = { it.setBitmap(bitmap) },
        modifier = modifier,
    )
}

private class PanoramaGlSurfaceView(context: Context) : GLSurfaceView(context) {
    private val panoramaRenderer = PanoramaGlRenderer()
    private var previousX = 0f
    private var previousY = 0f
    private var pinchDistance = 0f

    init {
        setEGLContextClientVersion(2)
        setRenderer(panoramaRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setBitmap(bitmap: Bitmap) {
        queueEvent { panoramaRenderer.setBitmap(bitmap) }
        requestRender()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> pinchDistance = pointerDistance(event)
            MotionEvent.ACTION_MOVE -> if (event.pointerCount >= 2) {
                val distance = pointerDistance(event)
                if (pinchDistance > 0f) panoramaRenderer.zoomBy((pinchDistance - distance) / max(width, height).toFloat())
                pinchDistance = distance
            } else {
                panoramaRenderer.panBy((event.x - previousX) / max(width, 1), (event.y - previousY) / max(height, 1))
                previousX = event.x
                previousY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pinchDistance = 0f
        }
        requestRender()
        return true
    }

    private fun pointerDistance(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(x * x + y * y)
    }
}

private class PanoramaGlRenderer : GLSurfaceView.Renderer {
    private var program = 0
    private var texture = 0
    private var bitmap: Bitmap? = null
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var yaw = 0f
    private var pitch = 0f
    private var fieldOfView = 70f
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0)
    }

    fun setBitmap(value: Bitmap) { bitmap = value }
    fun panBy(dx: Float, dy: Float) { yaw -= dx * 180f; pitch = (pitch + dy * 110f).coerceIn(-85f, 85f) }
    fun zoomBy(amount: Float) { fieldOfView = (fieldOfView + amount * 110f).coerceIn(35f, 100f) }

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        program = GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER, VERTEX))
            GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT))
            GLES20.glLinkProgram(program)
        }
        texture = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1); viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        bitmap?.let { android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, it, 0); bitmap = null }
        GLES20.glUseProgram(program)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uAspect"), viewportWidth.toFloat() / viewportHeight)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uYaw"), Math.toRadians(yaw.toDouble()).toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uPitch"), Math.toRadians(pitch.toDouble()).toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uFov"), Math.toRadians(fieldOfView.toDouble()).toFloat())
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun shader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
        GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader)
    }

    private companion object {
        const val VERTEX = "attribute vec2 aPosition; varying vec2 vPosition; void main(){ vPosition=aPosition; gl_Position=vec4(aPosition,0.0,1.0); }"
        const val FRAGMENT = """
            precision mediump float; varying vec2 vPosition; uniform sampler2D uTexture;
            uniform float uAspect; uniform float uYaw; uniform float uPitch; uniform float uFov;
            void main(){
                float t=tan(uFov*0.5); vec3 d=normalize(vec3(vPosition.x*uAspect*t,-vPosition.y*t,1.0));
                float cy=cos(uYaw), sy=sin(uYaw); d=vec3(cy*d.x+sy*d.z,d.y,-sy*d.x+cy*d.z);
                float cp=cos(uPitch), sp=sin(uPitch); d=vec3(d.x,cp*d.y-sp*d.z,sp*d.y+cp*d.z);
                float u=atan(d.x,d.z)/6.28318530718+0.5; float v=0.5-asin(clamp(d.y,-1.0,1.0))/3.14159265359;
                gl_FragColor=texture2D(uTexture,vec2(fract(u),clamp(v,0.0,1.0)));
            }
        """
    }
}
