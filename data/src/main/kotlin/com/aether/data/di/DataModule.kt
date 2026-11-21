package com.aether.data.di

import android.content.Context
import androidx.room.Room
import com.aether.data.AetherDatabase
import com.aether.data.action.ActionRecordDao
import com.aether.data.action.RoomActionRecordRepository
import com.aether.data.memory.MemoryDao
import com.aether.data.memory.RoomMemoryRepository
import com.aether.data.privacy.PiiEntityMapDao
import com.aether.data.privacy.RoomPiiRedactor
import com.aether.data.thread.RoomTrackedThreadRepository
import com.aether.data.thread.TrackedThreadDao
import com.aether.domain.action.ActionRecordRepository
import com.aether.domain.memory.MemoryRepository
import com.aether.domain.privacy.PiiRedactor
import com.aether.domain.thread.TrackedThreadRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the domain's storage ports to their Room implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(impl: RoomMemoryRepository): MemoryRepository

    @Binds
    @Singleton
    abstract fun bindTrackedThreadRepository(impl: RoomTrackedThreadRepository): TrackedThreadRepository

    @Binds
    @Singleton
    abstract fun bindActionRecordRepository(impl: RoomActionRecordRepository): ActionRecordRepository

    @Binds
    @Singleton
    abstract fun bindPiiRedactor(impl: RoomPiiRedactor): PiiRedactor

    companion object {

        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AetherDatabase =
            Room.databaseBuilder(context, AetherDatabase::class.java, AetherDatabase.NAME)
                .build()

        @Provides
        fun provideMemoryDao(database: AetherDatabase): MemoryDao = database.memoryDao()

        @Provides
        fun provideTrackedThreadDao(database: AetherDatabase): TrackedThreadDao = database.trackedThreadDao()

        @Provides
        fun provideActionRecordDao(database: AetherDatabase): ActionRecordDao = database.actionRecordDao()

        @Provides
        fun providePiiEntityMapDao(database: AetherDatabase): PiiEntityMapDao = database.piiEntityMapDao()
    }
}
