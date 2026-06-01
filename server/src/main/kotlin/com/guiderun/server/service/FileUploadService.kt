package com.guiderun.server.service

import com.guiderun.server.exception.AppException
import com.guiderun.server.exception.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 文件上传业务：当前用于评价语音留言，落本地磁盘按日期分目录组织。
 *
 * 安全要点：
 * - MIME 白名单 + 512KB 上限（对应客户端 30s AAC 录音）
 * - [resolveFile] 防路径穿越：归一化后必须以配置 baseDir 为前缀，越权返回 403
 */
@Service
class FileUploadService(
    @Value("\${guiderun.upload.base-dir:uploads}") private val baseDir: String,
) {

    private val allowedMimeTypes = setOf("audio/aac", "audio/m4a", "audio/mp3", "audio/mpeg", "audio/x-m4a")
    // 客户端录音上限 30 秒；128kbps AAC 下 30s ≈ 480KB，512KB 留出余量同时限制时长在 ~40s 以内
    private val maxSizeBytes = 512_000L

    fun uploadVoice(file: MultipartFile): String {
        if (file.size > maxSizeBytes)
            throw AppException(ErrorCode.INVALID_PARAM, "文件大小不能超过 1MB", HttpStatus.BAD_REQUEST)
        val contentType = file.contentType ?: ""
        if (contentType !in allowedMimeTypes)
            throw AppException(ErrorCode.INVALID_PARAM, "仅支持 AAC/M4A/MP3 格式", HttpStatus.BAD_REQUEST)

        val ext = when {
            contentType.contains("aac") -> "aac"
            contentType.contains("m4a") || contentType.contains("x-m4a") -> "m4a"
            else -> "mp3"
        }

        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val relPath = "voices/$today/${UUID.randomUUID()}.$ext"
        val fullPath = Paths.get(baseDir, relPath)
        Files.createDirectories(fullPath.parent)
        file.transferTo(fullPath)

        return "/api/v1/uploads/$relPath"
    }

    fun resolveFile(relativePath: String): java.io.File {
        val path = Paths.get(baseDir, relativePath).normalize()
        val base = Paths.get(baseDir).toAbsolutePath().normalize()
        if (!path.toAbsolutePath().startsWith(base))
            throw AppException(ErrorCode.FORBIDDEN_ACTION, "非法路径", HttpStatus.FORBIDDEN)
        val file = path.toFile()
        if (!file.exists()) throw AppException(ErrorCode.REQUEST_NOT_FOUND, "文件不存在", HttpStatus.NOT_FOUND)
        return file
    }
}
