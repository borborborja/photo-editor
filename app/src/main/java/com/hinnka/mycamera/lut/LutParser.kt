package com.hinnka.mycamera.lut

import android.content.Context
import android.util.Log
import com.hinnka.mycamera.color.TransferCurve
import com.hinnka.mycamera.raw.ColorSpace
import com.hinnka.mycamera.utils.PLog
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 3D LUT 解析器，支持 .cube (文本) 和 .plut (二进制) 格式
 */
object LutParser {
    private const val TAG = "LutParser"
    private const val MAGIC_PLUT = 0x54554C50 // 'PLUT' in Little Endian
    private const val PLUT_BASE_HEADER_BYTES = 16
    private const val MAX_PLUT_DIMENSION = 256
    private const val MAX_PLUT_PAYLOAD_BYTES = 64 * 1024 * 1024

    /**
     * 解析 LUT 文件（自动识别格式）
     */
    fun parse(inputStream: InputStream, title: String = ""): LutConfig {
        val stream = if (inputStream.markSupported()) inputStream else inputStream.buffered()

        // 先读取前 4 个字节判断是否为二进制格式
        val header = ByteArray(4)
        stream.mark(16)
        val read = stream.read(header)
        stream.reset()

        if (read == 4) {
            val magic = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int
            if (magic == MAGIC_PLUT) {
                return parseBinary(stream, title)
            }
        }

        // 默认回退到文本解析
        return CubeLutParser.parse(stream)
    }

    /**
     * 解析二进制 .plut 格式
     */
    private fun parseBinary(inputStream: InputStream, title: String): LutConfig {
        val baseHeader = ByteArray(PLUT_BASE_HEADER_BYTES)
        readFully(inputStream, baseHeader, "PLUT header")
        val base = ByteBuffer.wrap(baseHeader).order(ByteOrder.LITTLE_ENDIAN)
        val magic = base.int
        val version = base.int
        val size = base.int
        val dataType = base.int
        require(magic == MAGIC_PLUT) { "Invalid PLUT magic" }
        require(version in 1..3) { "Unsupported PLUT version: $version" }
        // A 256³ LUT is already a large offline asset; rejecting larger values prevents a
        // malformed import from overflowing the size calculation or exhausting heap memory.
        require(size in 2..MAX_PLUT_DIMENSION) { "Unsupported PLUT dimension: $size" }
        require(dataType == 0 || dataType == 1) { "Unsupported data type: $dataType" }
        val optionalHeaderBytes = (if (version >= 2) 4 else 0) + (if (version >= 3) 4 else 0)
        val metadata = ByteArray(optionalHeaderBytes)
        readFully(inputStream, metadata, "PLUT metadata")
        val metadataBuffer = ByteBuffer.wrap(metadata).order(ByteOrder.LITTLE_ENDIAN)
        val curveStorageId = if (version >= 2) metadataBuffer.int else TransferCurve.SRGB.storageId
        val curve = TransferCurve.fromStorageId(curveStorageId)

        val colorSpaceOrdinal = if (version >= 3) metadataBuffer.int else ColorSpace.SRGB.ordinal
        val colorSpace = ColorSpace.entries.getOrElse(colorSpaceOrdinal) { ColorSpace.SRGB }

        val count = size.toLong() * size.toLong() * size.toLong() * 3L
        val bytesPerComponent = if (dataType == 1) 2 else 1
        val expectedPayloadSize = count * bytesPerComponent.toLong()
        require(expectedPayloadSize <= MAX_PLUT_PAYLOAD_BYTES) { "PLUT payload is too large" }
        val data = ByteArray(expectedPayloadSize.toInt())
        readFully(inputStream, data, "PLUT payload")
        require(inputStream.read() == -1) { "PLUT contains trailing bytes" }

        //dataType 0 = UINT8, 1 = UINT16 (新支持), 2 = FLOAT32 (未来扩展)
        val expectedSize = expectedPayloadSize.toInt()
        val directBuffer = ByteBuffer.allocateDirect(expectedSize)
            .order(ByteOrder.nativeOrder())

        // 将数据拷贝到 DirectByteBuffer 以便 OpenGL 使用
        directBuffer.put(data)
        directBuffer.position(0)

        return LutConfig(
            size = size,
            byteBuffer = directBuffer,
            title = title,
            configDataType = if (dataType == 1) LutConfig.CONFIG_DATA_TYPE_UINT16 else LutConfig.CONFIG_DATA_TYPE_UINT8,
            curve = curve,
            colorSpace = colorSpace
        )
    }

    private fun readFully(input: InputStream, target: ByteArray, label: String) {
        var offset = 0
        while (offset < target.size) {
            val count = input.read(target, offset, target.size - offset)
            require(count > 0) { "Truncated $label" }
            offset += count
        }
    }

    /**
     * 从 Assets 文件夹解析 LUT 文件
     */
    fun parseFromAssets(context: Context, fileName: String): LutConfig {
        return context.assets.open(fileName).use { inputStream ->
            parse(inputStream, fileName.substringAfterLast('/').substringBeforeLast('.'))
        }
    }

    /**
     * 列出 Assets 中可用的 LUT 文件（从 config.json 读取）
     */
    fun listAvailableLuts(context: Context, folder: String = "luts"): List<LutInfo> {
        return try {
            // 读取 config.json
            val configPath = "$folder/config.json"
            val configJson = context.assets.open(configPath).use {
                it.bufferedReader().readText()
            }

            val jsonObject = JSONObject(configJson)
            val lutsArray = jsonObject.getJSONArray("luts")

            // 按配置文件中的顺序读取 LUT
            val lutList = mutableListOf<LutInfo>()
            for (i in 0 until lutsArray.length()) {
                val lutObj = lutsArray.getJSONObject(i)
                val id = lutObj.getString("id")
                val path = lutObj.getString("path")
                val nameObj = lutObj.getJSONObject("name")
                val isDefault = lutObj.optBoolean("isDefault", false)
                val isVip = lutObj.getBoolean("isVip")
                val category = lutObj.optString("category", "").trim()
                val isFavorite = lutObj.optBoolean("isFavorite", false)

                // 读取多语言名称
                val nameMap = mutableMapOf<String, String>()
                nameObj.keys().forEach { lang ->
                    nameMap[lang] = nameObj.getString(lang)
                }

                lutList.add(
                    LutInfo(
                        id = id,
                        nameMap = nameMap,
                        fileName = if (path.isBlank()) "" else "$folder/$path",
                        isBuiltIn = true,
                        isDefault = isDefault,
                        isVip = isVip,
                        category = category,
                        isFavorite = isFavorite
                    )
                )
            }

            PLog.d(TAG, "Loaded ${lutList.size} LUTs from config.json")
            lutList
        } catch (e: Exception) {
            PLog.w(TAG, "Failed to load LUT config", e)
            emptyList()
        }
    }
}
