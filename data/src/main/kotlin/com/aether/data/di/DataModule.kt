package com.aether.data.di

import android.content.Context
import androidx.room.Room
import com.aether.data.AetherDatabase
import com.aether.data.memory.MemoryDao
import com.aether.data.memory.RoomMemoryRepository
import com.aether.domain.memory.MemoryRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the domain's storage port to its Room implementation.
 *
 * This module is the only place the wiring is stated, so replacing the storage
 * engine later means editing one file.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(impl: RoomMemoryRepository): MemoryRepository

    companion object {

        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AetherDatabase =
            Room.databaseBuilder(context, AetherDatabase::class.java, AetherDatabase.NAME)
                .build()

        @Provides
        fun provideMemoryDao(database: AetherDatabase): MemoryDao = database.memoryDao()
    }
}
