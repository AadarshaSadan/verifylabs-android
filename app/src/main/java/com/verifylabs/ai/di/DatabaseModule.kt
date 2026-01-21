package com.verifylabs.ai.di

import android.content.Context
import androidx.room.Room
import com.verifylabs.ai.data.database.AppDatabase
import com.verifylabs.ai.data.database.VerificationDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "verifylabs_db").build()
    }

    @Provides
    fun provideVerificationDao(database: AppDatabase): VerificationDao {
        return database.verificationDao()
    }
}
