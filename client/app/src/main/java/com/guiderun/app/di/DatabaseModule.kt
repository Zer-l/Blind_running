package com.guiderun.app.di

import android.content.Context
import androidx.room.Room
import com.guiderun.app.data.local.AppDatabase
import com.guiderun.app.data.local.MIGRATION_2_3
import com.guiderun.app.data.local.dao.RunSessionStatsDao
import com.guiderun.app.data.local.dao.RunTrackBufferDao
import com.guiderun.app.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "guiderun.db")
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideRunTrackBufferDao(db: AppDatabase): RunTrackBufferDao = db.runTrackBufferDao()

    @Provides
    fun provideRunSessionStatsDao(db: AppDatabase): RunSessionStatsDao = db.runSessionStatsDao()
}
