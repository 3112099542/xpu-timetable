/*
 * WidgetRefresher.kt —— 小组件唯一刷新入口（M3，AC-18；M7 需求 4 增补前台刷新）
 *
 * 全部触发源（App 启动 / 数据变更 / 跨天闹钟 / 课后闹钟 / 回到前台）都只准调这里，
 * 幂等可重复调用。updateAll 重跑 TodayWidget.provideGlance，即重新取数渲染。
 *
 * 注意：调用方必须自行 runCatching —— 小组件刷新失败绝不能让导入/保存失败
 * （规格 §4.5：小组件刷新失败绝不能让业务操作失败）。
 */
package com.gould.xputimetable.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import java.util.concurrent.atomic.AtomicLong

object WidgetRefresher {

    /**
     * 生命周期触发的刷新最短间隔（M7 需求 4）。
     * App 前后台切换可能很频繁，若每次都重绘小组件会造成无谓的 launcher 重绘，
     * 故设一个下限；数据变更路径不走节流（见 [refreshAll]）。
     */
    private const val FOREGROUND_MIN_INTERVAL_MS = 30_000L

    private val lastForegroundRefreshAt = AtomicLong(0L)

    /** 刷新桌面上全部「今日课程」小组件实例（无实例时为空操作）。数据变更路径用它，不节流。 */
    suspend fun refreshAll(context: Context) {
        TodayWidget().updateAll(context.applicationContext)
    }

    /**
     * App 回到前台时调用（M7 需求 4）。
     *
     * **为什么需要它**：Android 的小组件（Glance 与 RemoteViews 皆然）**没有"变为可见"的回调**，
     * 系统也无从告知 App「用户正在看桌面」。原先的刷新全部由"进程内事件"驱动
     * （App 启动 / 数据变更），于是当用户长时间没进 App、又希望桌面上看到的是最新课表时，
     * 只能靠点进 App 触发一次进程启动来顺带刷新——这正是"点进去才刷新"的由来。
     *
     * 这里补上"回到前台"这个可控时机：用户只要跟 App 有过交互（切回来），
     * 桌面小组件就会被对齐一次。配合既有的跨天闹钟（00:05）与课后闹钟，
     * 三条链路共同保证"该看到新数据的时候就是新的"。
     *
     * 带节流：30 秒内的重复调用直接返回，避免频繁前后台切换造成无谓重绘。
     */
    suspend fun refreshOnForeground(context: Context) {
        val now = System.currentTimeMillis()
        val last = lastForegroundRefreshAt.get()
        if (now - last < FOREGROUND_MIN_INTERVAL_MS) return
        // CAS 保证并发调用只有一个能通过（生命周期回调可能来自不同线程）
        if (!lastForegroundRefreshAt.compareAndSet(last, now)) return
        refreshAll(context)
    }
}
