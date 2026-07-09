package com.example.toolbox.data.repository

import com.example.toolbox.data.local.dao.CountdownDao
import com.example.toolbox.data.local.entity.CountdownEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CountdownRepository @Inject constructor(
    private val dao: CountdownDao,
) {
    fun observeAll(): Flow<List<CountdownEntity>> = dao.observeAll()
    suspend fun add(entity: CountdownEntity) = dao.insert(entity)
    suspend fun update(entity: CountdownEntity) = dao.update(entity)
    suspend fun delete(entity: CountdownEntity) = dao.delete(entity)
    suspend fun togglePin(entity: CountdownEntity) = dao.update(entity.copy(isPinned = !entity.isPinned))
}
