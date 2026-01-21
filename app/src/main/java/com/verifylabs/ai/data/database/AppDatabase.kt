package com.verifylabs.ai.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [VerificationEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun verificationDao(): VerificationDao
}
