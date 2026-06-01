package com.guiderun.server.entity

import com.guiderun.server.common.Gender
import com.guiderun.server.common.ProvisioningStatus
import com.guiderun.server.common.UserStatus
import com.guiderun.server.entity.json.BlindProfileJson
import com.guiderun.server.entity.json.VolunteerProfileJson
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

/**
 * 用户实体（`users` 表）。
 *
 * - `roles` 以 JSON 列存储（同一 User 可同时持有 BLIND_RUNNER + VOLUNTEER）
 * - `blindProfile` / `volunteerProfile` 嵌入 JSON 列，避免多表 join
 * - rating 字段拆为 `ratingSum` + `ratingCount`，按需求即时算平均（避免浮点累计误差）
 * - 软删字段 `deletedAt` 预留，当前未启用
 */
@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @Column(length = 36)
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false, unique = true, length = 20)
    val phone: String,

    @Column(nullable = false, length = 50)
    var nickname: String,

    @Column(length = 500)
    var avatarUrl: String? = null,

    @Enumerated(EnumType.STRING)
    var gender: Gender? = null,

    @Type(JsonType::class)
    @Column(columnDefinition = "JSON", nullable = false)
    var roles: List<String> = emptyList(),

    @Type(JsonType::class)
    @Column(columnDefinition = "JSON")
    var blindProfile: BlindProfileJson? = null,

    @Type(JsonType::class)
    @Column(columnDefinition = "JSON")
    var volunteerProfile: VolunteerProfileJson? = null,

    @Column(nullable = false)
    var totalRuns: Int = 0,

    @Column(nullable = false)
    var totalHoursMinutes: Int = 0,

    @Column(nullable = false)
    var ratingSum: Int = 0,

    @Column(nullable = false)
    var ratingCount: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.ACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var provisioningStatus: ProvisioningStatus = ProvisioningStatus.ACTIVE,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column
    var deletedAt: Instant? = null,
)
