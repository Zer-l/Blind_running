package com.guiderun.server.controller

import com.guiderun.server.dto.ApiResponse
import com.guiderun.server.dto.common.ListResponse
import com.guiderun.server.dto.user.EmergencyContactDto
import com.guiderun.server.dto.user.UpdateUserRequest
import com.guiderun.server.service.BlindStatsService
import com.guiderun.server.service.ReviewService
import com.guiderun.server.service.UserService
import com.guiderun.server.service.VolunteerStatsService
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * 用户相关接口：当前用户资料 / 双端跑步统计 / 紧急联系人 CRUD / 个人评价分页。
 * 所有 `/me` 路径取登录态 [Authentication.getName] 作为 userId，无需路径参数。
 * 紧急联系人通过 `index` 定位（前端用列表下标增删改，业务上简单稳定）。
 */
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
    private val volunteerStatsService: VolunteerStatsService,
    private val blindStatsService: BlindStatsService,
    private val reviewService: ReviewService,
) {

    @GetMapping("/me")
    fun getMe(authentication: Authentication) =
        ApiResponse.ok(userService.getMe(authentication.name))

    @PatchMapping("/me")
    fun updateMe(
        authentication: Authentication,
        @Valid @RequestBody req: UpdateUserRequest,
    ) = ApiResponse.ok(userService.updateMe(authentication.name, req))

    @GetMapping("/me/volunteer-stats")
    fun volunteerStats(authentication: Authentication) =
        ApiResponse.ok(volunteerStatsService.getStats(authentication.name))

    @GetMapping("/me/blind-stats")
    fun blindStats(authentication: Authentication) =
        ApiResponse.ok(blindStatsService.getStats(authentication.name))

    @GetMapping("/me/blind-profile/emergency-contacts")
    fun getEmergencyContacts(authentication: Authentication) =
        ApiResponse.ok(userService.getEmergencyContacts(authentication.name))

    @PostMapping("/me/blind-profile/emergency-contacts")
    fun addEmergencyContact(
        authentication: Authentication,
        @Valid @RequestBody contact: EmergencyContactDto,
    ) = ApiResponse.ok(userService.addEmergencyContact(authentication.name, contact))

    @PatchMapping("/me/blind-profile/emergency-contacts/{index}")
    fun updateEmergencyContact(
        authentication: Authentication,
        @PathVariable index: Int,
        @Valid @RequestBody contact: EmergencyContactDto,
    ) = ApiResponse.ok(userService.updateEmergencyContact(authentication.name, index, contact))

    @DeleteMapping("/me/blind-profile/emergency-contacts/{index}")
    fun deleteEmergencyContact(
        authentication: Authentication,
        @PathVariable index: Int,
    ) = ApiResponse.ok(userService.deleteEmergencyContact(authentication.name, index))

    @GetMapping("/{userId}/reviews")
    fun getUserReviews(
        @PathVariable userId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = ApiResponse.ok(ListResponse.of(reviewService.getReviewsForUser(userId, PageRequest.of(page, size))))
}
