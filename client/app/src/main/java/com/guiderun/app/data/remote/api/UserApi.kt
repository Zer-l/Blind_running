package com.guiderun.app.data.remote.api

import com.guiderun.app.data.remote.dto.ApiResponse
import com.guiderun.app.data.remote.dto.BlindStatsDto
import com.guiderun.app.data.remote.dto.EmergencyContactDto
import com.guiderun.app.data.remote.dto.ListResponseDto
import com.guiderun.app.data.remote.dto.ReviewResponseDto
import com.guiderun.app.data.remote.dto.UpdateUserRequestDto
import com.guiderun.app.data.remote.dto.UserDto
import com.guiderun.app.data.remote.dto.VolunteerStatsDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 用户资料 Retrofit 接口。
 *
 * 涵盖：个人信息读写、视障/志愿者统计、紧急联系人 CRUD、历史评价查询。
 * 紧急联系人以 index（0-based）定位，服务端维护有序列表，客户端不存本地副本。
 */
interface UserApi {
    @GET("api/v1/users/me")
    suspend fun getMe(): ApiResponse<UserDto>

    @PATCH("api/v1/users/me")
    suspend fun updateMe(@Body request: UpdateUserRequestDto): ApiResponse<UserDto>

    @GET("api/v1/users/me/volunteer-stats")
    suspend fun getVolunteerStats(): ApiResponse<VolunteerStatsDto>

    @GET("api/v1/users/me/blind-stats")
    suspend fun getBlindStats(): ApiResponse<BlindStatsDto>

    @GET("api/v1/users/me/blind-profile/emergency-contacts")
    suspend fun getEmergencyContacts(): ApiResponse<List<EmergencyContactDto>>

    @POST("api/v1/users/me/blind-profile/emergency-contacts")
    suspend fun addEmergencyContact(@Body contact: EmergencyContactDto): ApiResponse<List<EmergencyContactDto>>

    @PATCH("api/v1/users/me/blind-profile/emergency-contacts/{index}")
    suspend fun updateEmergencyContact(
        @Path("index") index: Int,
        @Body contact: EmergencyContactDto,
    ): ApiResponse<List<EmergencyContactDto>>

    @DELETE("api/v1/users/me/blind-profile/emergency-contacts/{index}")
    suspend fun deleteEmergencyContact(@Path("index") index: Int): ApiResponse<List<EmergencyContactDto>>

    @GET("api/v1/users/{userId}/reviews")
    suspend fun getUserReviews(
        @Path("userId") userId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiResponse<ListResponseDto<ReviewResponseDto>>
}
