package com.guiderun.server.repository

import com.guiderun.server.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

/** User 表数据访问：除主键外按手机号查询用于登录环节。 */
interface UserJpaRepository : JpaRepository<UserEntity, String> {
    fun findByPhone(phone: String): UserEntity?
}
