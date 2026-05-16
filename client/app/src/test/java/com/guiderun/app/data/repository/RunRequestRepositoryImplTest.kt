package com.guiderun.app.data.repository

import com.guiderun.app.data.local.UserPreferences
import com.guiderun.app.data.remote.WebSocketManager
import com.guiderun.app.data.remote.api.RunRequestApi
import com.guiderun.app.domain.exception.InvalidStateTransitionException
import com.guiderun.app.domain.exception.RequestConflictException
import com.guiderun.app.domain.model.RunRequestStatus
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

class RunRequestRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: RunRequestRepositoryImpl

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        repo = RunRequestRepositoryImpl(
            api = retrofit.create(RunRequestApi::class.java),
            json = json,
            userPreferences = mockk(relaxed = true),
            webSocketManager = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    // ── 成功路径 ──────────────────────────────────────────────────────────

    @Test
    fun `createRunRequest 成功返回 MATCHING 状态的 RunRequest`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(successResponse(status = "MATCHING"))
                .addHeader("Content-Type", "application/json")
        )

        val params = com.guiderun.app.domain.model.CreateRunRequestParams(
            meetingLat = 39.9042,
            meetingLng = 116.4074,
            meetingDescription = "朝阳公园南门",
            expectedDurationMinutes = 30,
        )
        val result = repo.createRunRequest(params)

        assertTrue(result.isSuccess)
        assertEquals(RunRequestStatus.MATCHING, result.getOrThrow().status)
    }

    @Test
    fun `confirmMet 服务端已是 MET 状态时幂等返回成功`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(successResponse(status = "MET"))
                .addHeader("Content-Type", "application/json")
        )

        val result = repo.confirmMet("req-001")

        assertTrue(result.isSuccess)
        assertEquals(RunRequestStatus.MET, result.getOrThrow().status)
    }

    // ── 错误映射 ──────────────────────────────────────────────────────────

    @Test
    fun `accept 返回 409 时映射为 RequestConflictException`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody(errorResponse("REQUEST_ALREADY_MATCHED", "订单已被其他志愿者接受"))
                .addHeader("Content-Type", "application/json")
        )

        val result = repo.accept("req-001")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RequestConflictException)
    }

    @Test
    fun `accept 返回 400 INVALID_STATE_TRANSITION 时映射为 InvalidStateTransitionException`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(errorResponse("INVALID_STATE_TRANSITION", "订单当前状态不允许接单"))
                .addHeader("Content-Type", "application/json")
        )

        val result = repo.accept("req-001")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidStateTransitionException)
    }

    @Test
    fun `getRunRequest 返回 404 时映射为 RequestNotFoundException`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody(errorResponse("REQUEST_NOT_FOUND", "订单不存在"))
                .addHeader("Content-Type", "application/json")
        )

        val result = repo.getRunRequest("non-existent")

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull() is com.guiderun.app.domain.exception.RequestNotFoundException
        )
    }

    // ── 辅助方法 ──────────────────────────────────────────────────────────

    private fun successResponse(status: String) = """
        {
          "code": 0,
          "message": "OK",
          "data": {
            "id": "req-001",
            "status": "$status",
            "version": 0,
            "meetingLocation": { "lat": 39.9042, "lng": 116.4074, "description": "朝阳公园南门" },
            "expectedDurationMinutes": 30,
            "createdAt": 1700000000000
          }
        }
    """.trimIndent()

    private fun errorResponse(errorCode: String, message: String) = """
        {
          "code": 400,
          "message": "$message",
          "errorCode": "$errorCode"
        }
    """.trimIndent()
}
