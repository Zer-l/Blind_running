package com.guiderun.app.data.repository

import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.local.dao.UserDao
import com.guiderun.app.data.mapper.toDomain
import com.guiderun.app.data.mapper.toEntity
import com.guiderun.app.data.remote.api.UserApi
import com.guiderun.app.data.remote.dto.BlindProfileUpdateDto
import com.guiderun.app.data.remote.dto.EmergencyContactDto
import com.guiderun.app.data.remote.dto.UpdateUserRequestDto
import com.guiderun.app.data.remote.dto.VolunteerProfileUpdateDto
import com.guiderun.app.domain.model.BlindStats
import com.guiderun.app.domain.model.EmergencyContact
import com.guiderun.app.domain.model.Review
import com.guiderun.app.domain.model.UpdateProfileParams
import com.guiderun.app.domain.model.User
import com.guiderun.app.domain.model.UserRole
import com.guiderun.app.domain.model.VolunteerStats
import com.guiderun.app.domain.repository.UserRepository
import com.guiderun.app.util.runCatchingCancellable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApi: UserApi,
    private val userPreferences: UserPreferences,
    private val userDao: UserDao,
) : UserRepository {

    override suspend fun getMe(): Result<User> = runCatchingCancellable {
        val resp = userApi.getMe()
        if (resp.code != 0) error(resp.message)
        val user = requireNotNull(resp.data).toDomain()
        userDao.upsert(user.toEntity())
        user
    }

    override suspend fun updateRoles(roles: List<UserRole>): Result<User> = runCatchingCancellable {
        val resp = userApi.updateMe(
            UpdateUserRequestDto(roles = roles.map { it.name })
        )
        if (resp.code != 0) error(resp.message)
        val user = requireNotNull(resp.data).toDomain()
        userDao.upsert(user.toEntity())
        val activeRole = user.roles.firstOrNull()?.name ?: ""
        userPreferences.saveUserSession(user.id, activeRole)
        user
    }

    override suspend fun updateProfile(params: UpdateProfileParams): Result<User> = runCatchingCancellable {
        val resp = userApi.updateMe(
            UpdateUserRequestDto(
                nickname = params.nickname,
                gender = params.gender?.name,
                blindProfile = params.blindProfile?.let {
                    BlindProfileUpdateDto(
                        visionLevel = it.visionLevel,
                        preferredPaceSeconds = it.preferredPaceSeconds,
                        preferredDurationMinutes = it.preferredDurationMinutes,
                        medicalNotes = it.medicalNotes,
                        visualDescription = it.visualDescription,
                    )
                },
                volunteerProfile = params.volunteerProfile?.let {
                    VolunteerProfileUpdateDto(
                        averagePaceSeconds = it.averagePaceSeconds,
                        runningLevel = it.runningLevel,
                        hasGuideExperience = it.hasGuideExperience,
                    )
                },
            )
        )
        if (resp.code != 0) error(resp.message)
        val user = requireNotNull(resp.data).toDomain()
        userDao.upsert(user.toEntity())
        user
    }

    override suspend fun getVolunteerStats(): Result<VolunteerStats> = runCatchingCancellable {
        val resp = userApi.getVolunteerStats()
        if (resp.code != 0) error(resp.message)
        requireNotNull(resp.data).toDomain()
    }

    override suspend fun getBlindStats(): Result<BlindStats> = runCatchingCancellable {
        val resp = userApi.getBlindStats()
        if (resp.code != 0) error(resp.message)
        val data = requireNotNull(resp.data)
        BlindStats(
            totalRuns = data.totalRuns,
            totalDistanceMeters = data.totalDistanceMeters,
            totalDurationMinutes = data.totalDurationMinutes,
            currentMonthRuns = data.currentMonthRuns,
            averageRunDurationMinutes = data.averageRunDurationMinutes,
        )
    }

    override suspend fun getUserReviews(userId: String, page: Int, size: Int): Result<List<Review>> = runCatchingCancellable {
        val resp = userApi.getUserReviews(userId, page, size)
        if (resp.code != 0) error(resp.message)
        requireNotNull(resp.data).items.map { it.toDomain() }
    }

    override suspend fun getEmergencyContacts(): Result<List<EmergencyContact>> = runCatchingCancellable {
        val resp = userApi.getEmergencyContacts()
        if (resp.code != 0) error(resp.message)
        requireNotNull(resp.data).map { it.toDomain() }
    }

    override suspend fun addEmergencyContact(contact: EmergencyContact): Result<List<EmergencyContact>> = runCatchingCancellable {
        val resp = userApi.addEmergencyContact(contact.toDto())
        if (resp.code != 0) error(resp.message)
        requireNotNull(resp.data).map { it.toDomain() }
    }

    override suspend fun updateEmergencyContact(index: Int, contact: EmergencyContact): Result<List<EmergencyContact>> = runCatchingCancellable {
        val resp = userApi.updateEmergencyContact(index, contact.toDto())
        if (resp.code != 0) error(resp.message)
        requireNotNull(resp.data).map { it.toDomain() }
    }

    override suspend fun deleteEmergencyContact(index: Int): Result<List<EmergencyContact>> = runCatchingCancellable {
        val resp = userApi.deleteEmergencyContact(index)
        if (resp.code != 0) error(resp.message)
        requireNotNull(resp.data).map { it.toDomain() }
    }

    private fun EmergencyContact.toDto() = EmergencyContactDto(
        name = name,
        phone = phone,
        relationship = relationship,
    )

    private fun EmergencyContactDto.toDomain() = EmergencyContact(
        name = name,
        phone = phone,
        relationship = relationship,
    )
}
