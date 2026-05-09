package com.guiderun.server.dto.user

import com.guiderun.server.common.Gender
import com.guiderun.server.common.UserRole
import jakarta.validation.constraints.Size

data class UpdateUserRequest(
    @field:Size(max = 50, message = "昵称最多50个字符")
    val nickname: String? = null,
    val gender: Gender? = null,
    val roles: List<UserRole>? = null,
    val blindProfile: BlindProfileUpdateDto? = null,
    val volunteerProfile: VolunteerProfileUpdateDto? = null,
)
