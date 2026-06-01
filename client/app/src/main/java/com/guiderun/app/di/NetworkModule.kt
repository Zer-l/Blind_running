package com.guiderun.app.di

import com.guiderun.app.BuildConfig
import com.guiderun.app.data.remote.AuthInterceptor
import com.guiderun.app.data.remote.api.AuthApi
import com.guiderun.app.data.remote.api.RunRequestApi
import com.guiderun.app.data.remote.api.UserApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt 网络层依赖注入模块。
 *
 * 依赖链：Json → OkHttpClient（含 AuthInterceptor + 可选 LoggingInterceptor）→ Retrofit → Api 接口
 *
 * - [Json] 配置 ignoreUnknownKeys / coerceInputValues，容忍服务端字段增减，避免因字段缺失崩溃
 * - [OkHttpClient] 仅 Debug 包开启 BODY 级日志，Release 包不暴露请求内容
 * - Retrofit 使用 kotlinx.serialization 转换器，与 suspend 函数配合实现非阻塞网络调用
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
                    )
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi =
        retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideRunRequestApi(retrofit: Retrofit): RunRequestApi =
        retrofit.create(RunRequestApi::class.java)
}
