/*
 * TimetableApp.kt —— Application 子类（应用级装配入口）
 *
 * 作用：按架构 ADR-004（手动依赖注入，不引入 Hilt / Koin），在 Application 这一进程级单例上
 * 持有 AppContainer。AppContainer 集中创建并持有 Room 数据库与全部 DAO，是数据层的"总开关"。
 *
 * 为什么放在 Application 而不是 Activity：
 *   - 数据库是进程级资源，应在 Application 创建一次、全局共享；放在 Activity 会随配置变更
 *     （旋转屏幕）反复重建，既浪费又可能漏关连接；
 *   - 用 lazy 延迟初始化：首次真正访问 container.database 时才建库，避免冷启动就拖慢 Application。
 *
 * 配合 AndroidManifest 的 android:name=".TimetableApp"，系统会在进程启动时实例化本类。
 * M2-D：onCreate 里对齐一次"下一节课"提醒闹钟（重排时机①，规格 §3.2）。
 * M3：启动时刷新一次桌面小组件（刷新触发源 1）。
 * M6 需求 2：跨天刷新弃用 WorkManager 周期任务（实测漂移到次日约 07:33），
 *   改排 AlarmManager 每日 00:05 精确闹钟（DailyRefreshScheduler，幂等可重入）。
 */
package com.gould.xputimetable

import android.app.Application
import com.gould.xputimetable.di.AppContainer
import com.gould.xputimetable.widget.DailyRefreshScheduler
import kotlinx.coroutines.launch

class TimetableApp : Application() {

    /** 应用级依赖容器（惰性创建，首次访问 DAO 时才真正建库）。 */
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // 跨天刷新闹钟：幂等（重复 schedule 只覆盖同一 PendingIntent），失败不阻塞启动
        runCatching { DailyRefreshScheduler.schedule(this) }
        // 启动时对齐提醒闹钟（时机①）并刷新小组件（触发源 1）；失败不阻塞启动（AC-16）
        container.applicationScope.launch {
            container.onDataChanged()
        }
    }
}
