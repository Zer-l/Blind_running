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
