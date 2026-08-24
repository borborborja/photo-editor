package com.hinnka.mycamera.lut

import com.hinnka.mycamera.utils.BoundedTextLineReader
import java.io.InputStream

/**
 * Adobe .cube LUT 文件解析器
 *
 * 支持 3D LUT 的 .cube 文件格式解析
 * 参考: https://wwwimages2.adobe.com/content/dam/acom/en/products/speedgrade/cc/pdfs/cube-lut-specification-1.0.pdf
 */
object CubeLutParser {

    // .cube 文件关键字
    private const val KEYWORD_TITLE = "TITLE"
    private const val KEYWORD_LUT_3D_SIZE = "LUT_3D_SIZE"
    private const val KEYWORD_DOMAIN_MIN = "DOMAIN_MIN"
    private const val KEYWORD_DOMAIN_MAX = "DOMAIN_MAX"
    private const val MAX_CUBE_SIZE = 256
    private const val MAX_CUBE_PAYLOAD_BYTES = 64 * 1024 * 1024
    private const val MAX_CUBE_PENDING_SAMPLES = 1_000_000

    /**
     * 从 InputStream 解析 .cube 文件
     * 优化版本：单遍流式处理避免内存溢出
     */
    fun parse(inputStream: InputStream): LutConfig {
        var title = ""
        var size = 0
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        var data: FloatArray? = null
        var dataIndex = 0

        // 临时存储 RGB 数据（只在找到 size 之前使用）
        val tempDataList = mutableListOf<FloatArray>()

        inputStream.bufferedReader().use { reader ->
            BoundedTextLineReader.forEachLine(reader) { line ->
                val trimmedLine = line.trim()

                // 跳过空行和注释
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    return@forEachLine
                }

                when {
                    // 解析标题
                    trimmedLine.startsWith(KEYWORD_TITLE) -> {
                        title = extractQuotedString(trimmedLine)
                    }

                    // 解析 LUT 尺寸
                    trimmedLine.startsWith(KEYWORD_LUT_3D_SIZE) -> {
                        require(size == 0) { "Duplicate LUT_3D_SIZE" }
                        size = trimmedLine.substringAfter(KEYWORD_LUT_3D_SIZE).trim().toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid LUT_3D_SIZE")
                        require(size in 2..MAX_CUBE_SIZE) { "Unsupported LUT_3D_SIZE: $size" }
                        val expectedDataSize = expectedDataSize(size)
                        require(expectedDataSize.toLong() * 4L <= MAX_CUBE_PAYLOAD_BYTES) {
                            "LUT payload is too large"
                        }
                        require(tempDataList.size * 3 <= expectedDataSize) {
                            "Too many LUT samples before LUT_3D_SIZE"
                        }
                        // 找到 size 后，立即分配数组并写入已缓存的数据
                        val target = FloatArray(expectedDataSize)
                        data = target

                        // 将临时数据写入数组
                        for (values in tempDataList) {
                            target[dataIndex++] = normalizeValue(values[0], domainMin[0], domainMax[0])
                            target[dataIndex++] = normalizeValue(values[1], domainMin[1], domainMax[1])
                            target[dataIndex++] = normalizeValue(values[2], domainMin[2], domainMax[2])
                        }
                        tempDataList.clear()  // 释放临时列表内存
                    }

                    // 解析域最小值
                    trimmedLine.startsWith(KEYWORD_DOMAIN_MIN) -> {
                        domainMin = parseFloatTriple(trimmedLine.substringAfter(KEYWORD_DOMAIN_MIN))
                            ?: throw IllegalArgumentException("Invalid DOMAIN_MIN")
                    }

                    // 解析域最大值
                    trimmedLine.startsWith(KEYWORD_DOMAIN_MAX) -> {
                        domainMax = parseFloatTriple(trimmedLine.substringAfter(KEYWORD_DOMAIN_MAX))
                            ?: throw IllegalArgumentException("Invalid DOMAIN_MAX")
                    }

                    // 解析 RGB 数据行
                    else -> {
                        val values = parseFloatTriple(trimmedLine)
                        if (values != null) {
                            val target = data
                            if (target != null) {
                                require(dataIndex + 3 <= target.size) { "Too many LUT samples" }
                                // 已经分配了数组，直接写入
                                target[dataIndex++] = normalizeValue(values[0], domainMin[0], domainMax[0])
                                target[dataIndex++] = normalizeValue(values[1], domainMin[1], domainMax[1])
                                target[dataIndex++] = normalizeValue(values[2], domainMin[2], domainMax[2])
                            } else {
                                // 还未分配数组，暂存数据
                                require(tempDataList.size < MAX_CUBE_PENDING_SAMPLES) {
                                    "Too many LUT samples before LUT_3D_SIZE"
                                }
                                tempDataList.add(values)
                            }
                        }
                    }
                }
            }
        }

        if (size == 0 || data == null) {
            throw IllegalArgumentException("Invalid .cube file: LUT_3D_SIZE not specified")
        }

        val finalData = data
        val expectedDataSize = expectedDataSize(size)
        if (dataIndex != expectedDataSize) {
            throw IllegalArgumentException("Data size mismatch: expected $expectedDataSize, got $dataIndex")
        }

        return LutConfig(
            size = size,
            data = finalData,
            title = title
        )
    }

    /**
     * 提取引号中的字符串
     */
    private fun extractQuotedString(line: String): String {
        val start = line.indexOf('"')
        val end = line.lastIndexOf('"')
        return if (start >= 0 && end > start) {
            line.substring(start + 1, end)
        } else {
            line.substringAfter(' ').trim()
        }
    }

    /**
     * 解析包含三个浮点数的行
     */
    private fun parseFloatTriple(line: String): FloatArray? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 3) return null

        return try {
            floatArrayOf(
                parts[0].toFloat(),
                parts[1].toFloat(),
                parts[2].toFloat()
            ).takeIf { values -> values.all(Float::isFinite) }
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun expectedDataSize(size: Int): Int {
        val count = size.toLong() * size.toLong() * size.toLong() * 3L
        require(count <= Int.MAX_VALUE) { "LUT sample count is too large" }
        return count.toInt()
    }

    /**
     * 将值标准化到 [0, 1] 范围
     */
    private fun normalizeValue(value: Float, min: Float, max: Float): Float {
        require(min.isFinite() && max.isFinite() && max > min) {
            "Invalid LUT domain: $min..$max"
        }
        return if (min == 0f && max == 1f) {
            value.coerceIn(0f, 1f)
        } else {
            ((value - min) / (max - min)).coerceIn(0f, 1f)
        }
    }
}
