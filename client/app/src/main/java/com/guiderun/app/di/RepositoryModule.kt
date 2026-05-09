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
