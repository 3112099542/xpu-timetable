/*
 * TermDao.kt —— 学期表访问接口
 *
 * 作用：学期的读写与"当前激活学期"的观察。取激活学期是进入周视图的第一步，因此单独提供
 * observeActive（带 is_active 索引）。
 */
package com.gould.xputimetable.data.db.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.gould.xputimetable.data.db.entity.TermEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TermDao {

    @Insert
    suspend fun insert(term: TermEntity): Long

    @Update
    suspend fun update(term: TermEntity)

    @Delete
    suspend fun delete(term: TermEntity)

    @Query("SELECT * FROM terms WHERE id = :id")
    suspend fun getById(id: Long): TermEntity?

    @Query("SELECT * FROM terms WHERE is_active = 1 LIMIT 1")
    fun observeActive(): Flow<TermEntity?>

    @Query("SELECT * FROM terms ORDER BY start_date DESC")
    fun observeAll(): Flow<List<TermEntity>>
}
