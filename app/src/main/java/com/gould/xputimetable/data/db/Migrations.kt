/*
 * Migrations.kt —— 数据库迁移（版本升级脚本集中处）
 *
 * 铁律：改实体字段 ⇒ 必须升 `AppDatabase.version` 并在此补一条迁移；否则已装旧版的设备会命中
 *   IllegalStateException: Room cannot verify the data integrity ...
 * （真机+旧库才会暴露，构建与单测都是绿的）
 *
 * Room 3 的迁移签名（与 Room 2 教程不同，已用 javap 核对）：
 *   迁移体是**挂起函数**，接收 `androidx.sqlite.SQLiteConnection`，用 `execSQL` 执行 DDL。
 */
package com.gould.xputimetable.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.execSQL

/**
 * 1 → 2：course_sessions 新增 `week_list`（显式周次列表，CSV；NULL 表示沿用区间+单双周判定）。
 * 加的是可空列，ALTER TABLE 不影响既有一行数据 —— 用户已录入的课程完整保留。
 */
val MIGRATION_1_2: Migration = Migration(1, 2) { connection ->
    connection.execSQL("ALTER TABLE course_sessions ADD COLUMN week_list TEXT")
}
