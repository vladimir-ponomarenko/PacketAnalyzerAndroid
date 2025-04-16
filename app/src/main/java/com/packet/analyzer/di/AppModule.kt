package com.packet.analyzer.di

import android.content.Context
import com.packet.analyzer.data.datasource.root.RootDataSource
import com.packet.analyzer.data.repository.TrafficRepository
import com.packet.analyzer.data.repository.TrafficRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideRootDataSource(@ApplicationContext context: Context): RootDataSource {
        return RootDataSource(context)
    }

    @Provides
    @Singleton
    fun provideTrafficRepository(
        context: Context,
        rootDataSource: RootDataSource
        // nativeDataSource: NativeTrafficDataSource
    ): TrafficRepository {
        return TrafficRepositoryImpl(context, rootDataSource /*, nativeDataSource */)
    }
}