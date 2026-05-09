package com.guiderun.server.repository

import com.guiderun.server.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<UserEntity, String> {
    fun findByPhone(phone: String): UserEntity?
}
