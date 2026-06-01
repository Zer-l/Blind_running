package com.guiderun.app.di

import com.guiderun.app.data.repository.AuthRepositoryImpl
import com.guiderun.app.data.repository.RunRequestRepositoryImpl
import com.guiderun.app.data.repository.UserRepositoryImpl
import com.guiderun.app.domain.repository.AuthRepository
import com.guiderun.app.domain.repository.RunRequestRepository
import com.guiderun.app.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository 接口与实现的绑定模块。
 *
 * Domain 层只依赖接口（AuthRepository / UserRepository / RunRequestRepository），
 * 此处通过 @Binds 将 data 层的实现类注入进来，实现依赖倒置。
 * 切换数据源（如 Mock → 真实 API）只需修改此模块，业务逻辑层无感知。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindRunRequestRepository(impl: RunRequestRepositoryImpl): RunRequestRepository
}
