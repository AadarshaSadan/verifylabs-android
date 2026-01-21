package com.verifylabs.ai.data.repository

import com.verifylabs.ai.data.database.VerificationDao
import com.verifylabs.ai.data.database.VerificationEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class VerificationRepository @Inject constructor(private val dao: VerificationDao) {
    fun getAllHistory(): Flow<List<VerificationEntity>> = dao.getAllFlow()

    fun getHistoryByType(type: String): Flow<List<VerificationEntity>> = dao.getByTypeFlow(type)

    suspend fun saveVerification(entity: VerificationEntity): Long = dao.insert(entity)

    suspend fun getById(id: Long): VerificationEntity? = dao.getById(id)

    suspend fun deleteOlderThan(days: Int): Int {
        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        return dao.deleteOlderThan(cutoffTime)
    }

    suspend fun deleteById(id: Long): Int = dao.deleteById(id)

    suspend fun purgeAll(): Int = dao.deleteAll()

    suspend fun getTotalSizeKb(): Long = dao.getTotalSizeKb() ?: 0L
}
