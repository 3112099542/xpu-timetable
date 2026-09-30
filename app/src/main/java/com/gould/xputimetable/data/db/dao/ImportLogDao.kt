/*
 * ImportLogDao.kt —— 导入审计表访问接口
 *
 * 作用：写入与查看导入历史。审计价值在于事后回答"这次导入替换了多少门、保留了多少手动课程"。
 */
package com.gould.xputimetable.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import com.gould.xputimetable.data.db.entity.ImportLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportLogDao {

    @Insert
    suspend fun insert(log: ImportLogEntity): Long

    @Query("SELECT * FROM import_logs ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ImportLogEntity>>

    @Query("SELECT * FROM import_logs ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatest(): ImportLogEntity?
}
