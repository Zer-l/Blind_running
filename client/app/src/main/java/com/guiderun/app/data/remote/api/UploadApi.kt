package com.guiderun.app.data.remote.api

import com.guiderun.app.data.remote.dto.ApiResponse
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

@Serializable
data class VoiceUploadResponse(val voiceUrl: String)

interface UploadApi {

    @Multipart
    @POST("api/v1/uploads/voice")
    suspend fun uploadVoice(@Part file: MultipartBody.Part): ApiResponse<VoiceUploadResponse>
}
