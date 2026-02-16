package com.example.robotarmcontroller.di

import com.example.robotarmcontroller.data.*
import com.example.robotarmcontroller.data.tinyframe.BleTinyFramePort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RobotModule {

    @Provides
    @Singleton
    fun provideServoRepository(
        tinyFramePort: BleTinyFramePort
    ): ServoRepository {
        return ServoRepository(tinyFramePort)
    }

    @Provides
    @Singleton
    fun provideMotionRepository(
        tinyFramePort: BleTinyFramePort
    ): MotionRepository {
        return MotionRepository(tinyFramePort)
    }

    @Provides
    @Singleton
    fun provideCycleRepository(
        tinyFramePort: BleTinyFramePort
    ): CycleRepository {
        return CycleRepository(tinyFramePort)
    }
}