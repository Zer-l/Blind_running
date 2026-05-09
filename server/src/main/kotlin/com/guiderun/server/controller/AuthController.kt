package com.guiderun.server.controller

import com.guiderun.server.dto.ApiResponse
import com.guiderun.server.dto.auth.LoginRequest
import com.guiderun.server.dto.auth.RefreshRequest
import com.guiderun.server.dto.auth.SendSmsRequest
import com.guiderun.server.service.AuthService
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/send-sms")
    fun sendSms(@Valid @RequestBody req: SendSmsRequest): ApiResponse<Unit> {
        authService.sendSms(req.phone)
        return ApiResponse.ok()
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest) =
        ApiResponse.ok(authService.login(req.phone, req.smsCode))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody req: RefreshRequest) =
        ApiResponse.ok(authService.refresh(req.refreshToken))

    @PostMapping("/logout")
    fun logout(authentication: Authentication): ApiResponse<Unit> {
        authService.logout(authentication.name)
        return ApiResponse.ok()
    }
}
