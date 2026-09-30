/*
 * TransactionRunner.kt —— 事务抽象（为可测试性）
 *
 * 作用：把"在数据库事务里执行一段逻辑"这个能力抽象成接口。
 *
 * 为什么要抽：团队要求单测用假 DAO，**禁止引入 Robolectric / MockK**（Spec 约束：
 * 不新增测试依赖）。把事务抽成接口后，测试可传入"直接执行 block"的假实现，
 * 从而在不建真实 SQLite 连接的情况下验证 upsertCourse / applyImport 的组装逻辑；
 * 生产实现用 Room 3 的 withWriteTransaction（Room 3 已无 Room 2 的 runInTransaction）。
 */
package com.gould.xputimetable.data.repository

import androidx.room3.withWriteTransaction
import com.gould.xputimetable.data.db.AppDatabase

interface TransactionRunner {
    suspend fun <R> run(block: suspend () -> R): R
}

/**
 * 生产用事务实现：包住 Room 3 的 withWriteTransaction。
 *
 * 注意：withWriteTransaction 是 androidx.room3 的**顶层扩展函数**（不是 RoomDatabase 的成员方法），
 * 必须显式 import androidx.room3.withWriteTransaction，否则会报 Unresolved reference。
 */
class RoomTransactionRunner(
    private val database: AppDatabase,
) : TransactionRunner {

    /**
     * 注意：Room 3 的 withWriteTransaction 接收的是带 TransactionScope receiver 的挂起 lambda，
     * 因此这里要写成 `{ block() }` 而不是直接传 block。
     */
    override suspend fun <R> run(block: suspend () -> R): R = database.withWriteTransaction { block() }
}
