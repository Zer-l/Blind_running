package com.guiderun.server.dto.auth

/** 刷新响应：仅返回新 accessToken，refreshToken 保持复用直到过期或撤销。 */
data class RefreshResponse(val accessToken: String)
