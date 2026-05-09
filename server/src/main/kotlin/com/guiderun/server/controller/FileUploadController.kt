package com.guiderun.server.controller

import com.guiderun.server.dto.ApiResponse
import com.guiderun.server.service.FileUploadService
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

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
