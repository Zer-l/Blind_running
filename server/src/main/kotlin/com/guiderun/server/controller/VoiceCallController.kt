package com.guiderun.server.controller

import com.guiderun.server.dto.ApiResponse
import com.guiderun.server.dto.run.VoiceCallResponse
import com.guiderun.server.service.VoiceCallService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/run-requests")
class VoiceCallController(
    private val voiceCallService: VoiceCallService,
) {

    @PostMapping("/{id}/voice-call/initiate")
    fun initiate(
        authentication: Authentication,
        @PathVariable id: String,
    ): ApiResponse<VoiceCallResponse> =
        ApiResponse.ok(voiceCallService.initiate(authentication.name, id))

    @PostMapping("/{id}/voice-call/end")
    fun end(
        authentication: Authentication,
        @PathVariable id: String,
    ): ApiResponse<Unit> =
        ApiResponse.ok(voiceCallService.end(authentication.name, id))
}
