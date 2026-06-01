package com.guiderun.server.controller

import com.guiderun.server.dto.ApiResponse
import com.guiderun.server.service.FileUploadService
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/**
 * 文件上传与回放接口（当前用于评价语音留言）。
 * - 上传：`multipart/form-data`，由 [FileUploadService] 落本地磁盘并生成相对路径
 * - 回放：`/voices/` 前缀通配匹配子路径，按扩展名映射 Content-Type
 * - 鉴权：依赖 Spring Security 全局保护 `/api/v1/` 前缀，未登录直接 401
 */
@RestController
@RequestMapping("/api/v1/uploads")
class FileUploadController(private val uploadService: FileUploadService) {

    @PostMapping("/voice", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadVoice(@RequestParam("file") file: MultipartFile): ApiResponse<Map<String, String>> {
        val url = uploadService.uploadVoice(file)
        return ApiResponse.ok(mapOf("voiceUrl" to url))
    }

    // 鉴权由 Spring Security 全局保护，/api/v1/** 均需登录
    @GetMapping("/voices/**")
    fun serveVoice(request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<FileSystemResource> {
        val fullPath = request.requestURI
        val prefix = "/api/v1/uploads/voices/"
        val relative = "voices/" + fullPath.substringAfter(prefix)
        val file = uploadService.resolveFile(relative)
        val mediaType = when {
            relative.endsWith(".aac") -> MediaType.parseMediaType("audio/aac")
            relative.endsWith(".m4a") -> MediaType.parseMediaType("audio/m4a")
            else -> MediaType.parseMediaType("audio/mpeg")
        }
        return ResponseEntity.ok().contentType(mediaType).body(FileSystemResource(file))
    }
}
