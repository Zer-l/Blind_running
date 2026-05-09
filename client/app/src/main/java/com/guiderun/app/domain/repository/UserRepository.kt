package com.guiderun.app.domain.repository

import com.guiderun.app.domain.model.BlindStats
import com.guiderun.app.domain.model.EmergencyContact
import com.guiderun.app.domain.model.Review
import com.guiderun.app.domain.model.UpdateProfileParams
import com.guiderun.app.domain.model.User
import com.guiderun.app.domain.model.UserRole
import com.guiderun.app.domain.model.VolunteerStats

interface UserRepository {
    suspend fun getMe(): Result<User>
    suspend fun updateRoles(roles: List<UserRole>): Result<User>
    suspend fun updateProfile(params: UpdateProfileParams): Result<User>
    suspend fun getVolunteerStats(): Result<VolunteerStats>
    suspend fun getBlindStats(): Result<BlindStats>
    suspend fun getUserReviews(userId: String, page: Int = 0, size: Int = 20): Result<List<Review>>
    suspend fun getEmergencyContacts(): Result<List<EmergencyContact>>
    suspend fun addEmergencyContact(contact: EmergencyContact): Result<List<EmergencyContact>>
    suspend fun updateEmergencyContact(index: Int, contact: EmergencyContact): Result<List<EmergencyContact>>
    suspend fun deleteEmergencyContact(index: Int): Result<List<EmergencyContact>>
}
