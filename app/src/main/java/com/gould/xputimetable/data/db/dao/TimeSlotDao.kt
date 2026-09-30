/*
 * TimeSlotDao.kt —— 节次作息表访问接口
 *
 * 作用：作息的读写与观察。周视图用它渲染左侧时间轴，小组件用它把节次换算成时刻。
 */
package com.gould.xputimetable.data.db.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.gould.xputimetable.data.db.entity.TimeSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeSlotDao {

    @Insert
    suspend fun insert(slot: TimeSlotEntity)

    @Insert
    suspend fun insertAll(slots: List<TimeSlotEntity>)

    @Update
    suspend fun update(slot: TimeSlotEntity)

    @Delete
    suspend fun delete(slot: TimeSlotEntity)

    @Query("SELECT * FROM time_slots WHERE section = :section")
    suspend fun getBySection(section: Int): TimeSlotEntity?

    @Query("SELECT * FROM time_slots ORDER BY section ASC")
    fun observeAll(): Flow<List<TimeSlotEntity>>
}
