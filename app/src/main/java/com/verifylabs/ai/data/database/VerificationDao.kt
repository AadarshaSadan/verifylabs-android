package com.verifylabs.ai.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VerificationDao {
    @Insert suspend fun insert(verification: VerificationEntity): Long

    @Query("SELECT * FROM verification_history ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<VerificationEntity>>

    @Query("SELECT * FROM verification_history WHERE id = :id")
    suspend fun getById(id: Long): VerificationEntity?

    @Query("SELECT * FROM verification_history WHERE mediaType = :type ORDER BY timestamp DESC")
    fun getByTypeFlow(type: String): Flow<List<VerificationEntity>>

    @Query("DELETE FROM verification_history WHERE timestamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long): Int

    @Query("DELETE FROM verification_history") suspend fun deleteAll(): Int

    @Query("SELECT SUM(fileSizeKb) FROM verification_history") suspend fun getTotalSizeKb(): Long?
}
