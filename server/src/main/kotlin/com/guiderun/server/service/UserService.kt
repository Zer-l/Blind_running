package com.guiderun.server.service

import com.guiderun.server.common.ProvisioningStatus
import com.guiderun.server.dto.user.BlindProfileUpdateDto
import com.guiderun.server.dto.user.EmergencyContactDto
import com.guiderun.server.dto.user.UpdateUserRequest
import com.guiderun.server.dto.user.UserDto
import com.guiderun.server.dto.user.VolunteerProfileUpdateDto
import com.guiderun.server.entity.json.BlindProfileJson
import com.guiderun.server.entity.json.EmergencyContactJson
import com.guiderun.server.entity.json.VolunteerProfileJson
import com.guiderun.server.exception.AppException
import com.guiderun.server.exception.ErrorCode
import com.guiderun.server.mapper.toDto
import com.guiderun.server.repository.UserJpaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class UserService(private val userRepo: UserJpaRepository) {

    companion object {
        private const val MAX_EMERGENCY_CONTACTS = 5
    }

    @Transactional(readOnly = true)
    fun getMe(userId: String): UserDto =
        userRepo.findById(userId).orElseThrow {
            AppException(ErrorCode.USER_NOT_FOUND, "用户不存在")
        }.toDto()

    fun updateMe(userId: String, req: UpdateUserRequest): UserDto {
        val user = userRepo.findById(userId).orElseThrow {
            AppException(ErrorCode.USER_NOT_FOUND, "用户不存在")
        }
        req.nickname?.let { user.nickname = it }
        req.gender?.let { user.gender = it }
        req.roles?.let { newRoles ->
            val wasEmpty = user.roles.isEmpty()
            user.roles = newRoles.map { r -> r.name }
            if (wasEmpty && user.roles.isNotEmpty()) {
                user.provisioningStatus = ProvisioningStatus.ACTIVE
            }
        }
        req.blindProfile?.let { updateBlindProfile(user, it) }
        req.volunteerProfile?.let { updateVolunteerProfile(user, it) }
        user.updatedAt = Instant.now()
        return userRepo.save(user).toDto()
    }

    private fun updateBlindProfile(user: com.guiderun.server.entity.UserEntity, update: BlindProfileUpdateDto) {
        val existing = user.blindProfile ?: BlindProfileJson(visionLevel = "unknown")
        user.blindProfile = existing.copy(
            visionLevel = update.visionLevel ?: existing.visionLevel,
            preferredPaceSeconds = update.preferredPaceSeconds ?: existing.preferredPaceSeconds,
            preferredDurationMinutes = update.preferredDurationMinutes ?: existing.preferredDurationMinutes,
            medicalNotes = update.medicalNotes ?: existing.medicalNotes,
            visualDescription = update.visualDescription ?: existing.visualDescription,
        )
    }

    private fun updateVolunteerProfile(user: com.guiderun.server.entity.UserEntity, update: VolunteerProfileUpdateDto) {
        val existing = user.volunteerProfile ?: VolunteerProfileJson()
        user.volunteerProfile = existing.copy(
            averagePaceSeconds = update.averagePaceSeconds ?: existing.averagePaceSeconds,
            runningLevel = update.runningLevel ?: existing.runningLevel,
            hasGuideExperience = update.hasGuideExperience ?: existing.hasGuideExperience,
        )
    }

    @Transactional(readOnly = true)
    fun getEmergencyContacts(userId: String): List<EmergencyContactDto> {
        val user = userRepo.findById(userId).orElseThrow {
            AppException(ErrorCode.USER_NOT_FOUND, "用户不存在", HttpStatus.NOT_FOUND)
        }
        val blindProfile = user.blindProfile ?: return emptyList()
        return blindProfile.emergencyContacts.map { it.toDto() }
    }

    fun addEmergencyContact(userId: String, contact: EmergencyContactDto): List<EmergencyContactDto> {
        val user = userRepo.findById(userId).orElseThrow {
            AppException(ErrorCode.USER_NOT_FOUND, "用户不存在", HttpStatus.NOT_FOUND)
        }
        val blindProfile = user.blindProfile ?: BlindProfileJson(visionLevel = "unknown")

        if (blindProfile.emergencyContacts.size >= MAX_EMERGENCY_CONTACTS) {
            throw AppException(
                ErrorCode.INVALID_STATE_TRANSITION,
                "紧急联系人数量已达上限（$MAX_EMERGENCY_CONTACTS 个）",
                HttpStatus.BAD_REQUEST,
            )
        }

        val updatedContacts = blindProfile.emergencyContacts + contact.toEntity()
        user.blindProfile = blindProfile.copy(emergencyContacts = updatedContacts)
        user.updatedAt = Instant.now()
        userRepo.save(user)

        return updatedContacts.map { it.toDto() }
    }

    fun updateEmergencyContact(userId: String, index: Int, contact: EmergencyContactDto): List<EmergencyContactDto> {
        val user = userRepo.findById(userId).orElseThrow {
            AppException(ErrorCode.USER_NOT_FOUND, "用户不存在", HttpStatus.NOT_FOUND)
        }
        val blindProfile = user.blindProfile ?: throw AppException(
            ErrorCode.INVALID_STATE_TRANSITION, "没有紧急联系人", HttpStatus.BAD_REQUEST
        )

        if (index < 0 || index >= blindProfile.emergencyContacts.size) {
            throw AppException(ErrorCode.INVALID_STATE_TRANSITION, "联系人索引无效", HttpStatus.BAD_REQUEST)
        }

        val updatedContacts = blindProfile.emergencyContacts.toMutableList()
        updatedContacts[index] = contact.toEntity()
        user.blindProfile = blindProfile.copy(emergencyContacts = updatedContacts)
        user.updatedAt = Instant.now()
        userRepo.save(user)

        return updatedContacts.map { it.toDto() }
    }

    fun deleteEmergencyContact(userId: String, index: Int): List<EmergencyContactDto> {
        val user = userRepo.findById(userId).orElseThrow {
            AppException(ErrorCode.USER_NOT_FOUND, "用户不存在", HttpStatus.NOT_FOUND)
        }
        val blindProfile = user.blindProfile ?: throw AppException(
            ErrorCode.INVALID_STATE_TRANSITION, "没有紧急联系人", HttpStatus.BAD_REQUEST
        )

        if (index < 0 || index >= blindProfile.emergencyContacts.size) {
            throw AppException(ErrorCode.INVALID_STATE_TRANSITION, "联系人索引无效", HttpStatus.BAD_REQUEST)
        }

        val updatedContacts = blindProfile.emergencyContacts.toMutableList()
        updatedContacts.removeAt(index)
        user.blindProfile = blindProfile.copy(emergencyContacts = updatedContacts)
        user.updatedAt = Instant.now()
        userRepo.save(user)

        return updatedContacts.map { it.toDto() }
    }

    private fun EmergencyContactDto.toEntity() = EmergencyContactJson(
        name = name,
        phone = phone,
        relationship = relationship,
    )

    private fun EmergencyContactJson.toDto() = EmergencyContactDto(
        name = name,
        phone = phone,
        relationship = relationship,
    )
}
