package com.guiderun.app.di

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.guiderun.app.data.location.FusedLocationProviderImpl
import com.guiderun.app.data.location.LegacyLocationProviderImpl
import com.guiderun.app.domain.repository.LocationProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 定位提供者依赖注入模块。
 *
 * 运行时检测 Google Play Services 是否可用：
 * - 可用 → 注入 [FusedLocationProviderImpl]（精度高、省电，GMS 设备标配）
 * - 不可用（国内纯 AOSP / 部分厂商 ROM）→ 注入 [LegacyLocationProviderImpl]（原生 LocationManager）
 *
 * 调用方（Service / ViewModel）只依赖 [LocationProvider] 接口，无需关心底层实现。
 */
@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideLocationProvider(@ApplicationContext context: Context): LocationProvider {
        val gmsAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        return if (gmsAvailable) {
            FusedLocationProviderImpl(context)
        } else {
            LegacyLocationProviderImpl(context)
        }
    }
}
