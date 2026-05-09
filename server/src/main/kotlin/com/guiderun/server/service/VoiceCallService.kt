package com.guiderun.server.service

import com.guiderun.server.common.RunRequestStatus
import com.guiderun.server.dto.run.VoiceCallResponse
import com.guiderun.server.exception.AppException
import com.guiderun.server.exception.ErrorCode
import com.guiderun.server.repository.RunRequestJpaRepository
import com.guiderun.server.repository.UserJpaRepository
import com.guiderun.server.websocket.GuideRunWebSocketHandler
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class VoiceCallService(
    private val requestRepo: RunRequestJpaRepository,
    private val userRepo: UserJpaRepository,
    private val wsHandler: GuideRunWebSocketHandler,
) {

    fun initiate(userId: String, requestId: String): VoiceCallResponse {
        val request = requestRepo.findById(requestId).orElseThrow {
            AppException(ErrorCode.REQUEST_NOT_FOUND, "订单不存在", HttpStatus.NOT_FOUND)
        }

        if (request.status != RunRequestStatus.RUNNING) {
            throw AppException(
                ErrorCode.INVALID_STATE_TRANSITION,
                "只有跑步中状态才能发起通话",
                HttpStatus.BAD_REQUEST,
            )
        }

        val isBlind = request.blindRunnerId == userId
        val isVolunteer = request.volunteerId == userId
        if (!isBlind && !isVolunteer) {
            throw AppException(ErrorCode.FORBIDDEN, "无权操作此订单", HttpStatus.FORBIDDEN)
        }

        val otherUserId = if (isBlind) request.volunteerId else request.blindRunnerId
        val otherUser = userRepo.findById(otherUserId!!).orElseThrow {
            AppException(ErrorCode.USER_NOT_FOUND, "用户不存在", HttpStatus.NOT_FOUND)
        }

        val currentUser = userRepo.findById(userId).orElseThrow {
            AppException(ErrorCode.USER_NOT_FOUND, "用户不存在", HttpStatus.NOT_FOUND)
        }

        // Send WS notification
        wsHandler.pushVoiceCallInvited(
            requestId = requestId,
            toUserId = otherUserId,
            fromNickname = currentUser.nickname,
            roomId = "",
            userSig = "",
            sdkAppId = 0,
        )

        return VoiceCallResponse(
            requestId = requestId,
            otherPartyPhone = maskPhone(otherUser.phone),
            status = "initiated",
        )
    }

    fun end(userId: String, requestId: String) {
        val request = requestRepo.findById(requestId).orElseThrow {
            AppException(ErrorCode.REQUEST_NOT_FOUND, "订单不存在", HttpStatus.NOT_FOUND)
        }

        val participants = listOfNotNull(request.blindRunnerId, request.volunteerId)
        if (userId !in participants) {
            throw AppException(ErrorCode.FORBIDDEN, "无权操作此订单", HttpStatus.FORBIDDEN)
        }

        wsHandler.pushVoiceCallEnded(requestId, participants)
    }

    private fun maskPhone(phone: String): String {
        if (phone.length < 7) return phone
        return phone.substring(0, 3) + "****" + phone.substring(phone.length - 4)
    }
}
