package com.flechazo.toolbox.feature.countdown

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 倒数日数据出口。只负责存取，不做任何时间语义推导 —— 那属于 [CountdownEngine]（纯函数）。
 */
@Singleton
class CountdownRepository @Inject constructor(private val dao: CountdownDao) {

    fun observeAll(): Flow<List<CountdownEntity>> = dao.observeAll()

    /** 供提醒调度与导出使用的一次性快照。 */
    suspend fun snapshot(): List<CountdownEntity> = dao.snapshot()

    suspend fun byId(id: Long): CountdownEntity? = dao.byId(id)

    suspend fun add(entity: CountdownEntity): Long = dao.insert(entity)

    suspend fun update(entity: CountdownEntity) = dao.update(entity)

    /**
     * 删除并**返回被删掉的实体**，交给上层做"撤销"。
     *
     * 撤销靠 [restore] 原样写回：`id` 非 0 时 Room 不会重新自增，
     * 所以 `createdAt` 与 id 都不变，提醒与排序也不会错位。
     */
    suspend fun deleteAndGet(id: Long): CountdownEntity? {
        val existing = dao.byId(id) ?: return null
        dao.delete(existing)
        return existing
    }

    suspend fun restore(entity: CountdownEntity) = dao.upsert(entity)

    suspend fun clear() = dao.clear()

    /** 新建事件的统一入口：补齐时间戳，避免各调用点漏字段。 */
    suspend fun create(entity: CountdownEntity): Long {
        val now = System.currentTimeMillis()
        return dao.insert(entity.copy(createdAt = if (entity.createdAt == 0L) now else entity.createdAt, updatedAt = now))
    }

    suspend fun save(entity: CountdownEntity) = dao.update(entity.copy(updatedAt = System.currentTimeMillis()))

    // ---- 兼容旧调用点（LegacyImporter）----

    suspend fun add(title: String, date: String, type: Int) {
        dao.insert(CountdownEntity(title = title, date = date, type = type))
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
