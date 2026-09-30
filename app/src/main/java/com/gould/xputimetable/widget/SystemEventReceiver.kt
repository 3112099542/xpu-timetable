/*
 * SystemEventReceiver.kt —— 系统事件恢复（M2-D 时机③④；M6 需求 2/3 增补；M7 移出提醒包）
 *
 * 设备重启 / 系统时间或时区变化 / 精确闹钟授权变化 / 覆盖安装后，小组件的一次性闹钟
 * 已失效或失准，统一在此重排：
 *   1. 跨天刷新闹钟（DailyRefreshScheduler，幂等）；
 *   2. 刷新小组件 —— provideGlance 尾部会顺带重排「课程结束」刷新闹钟（4002），
 *      因此这里刷新一次即等于把两条小组件闹钟都钉回最新。
 *
 * M7：课前提醒功能已整体移除，本接收器不再做提醒重排（原先还会调 ReminderManager.reschedule）。
 * 职责只剩小组件，故从 reminder 包移到 widget 包，与它服务的两个调度器同包。
 * 刷新是挂起操作，故用 goAsync + 应用级协程作用域。
 */
package com.gould.xputimetable.widget

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gould.xputimetable.TimetableApp
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val app = context.applicationContext as TimetableApp
        val pendingResult = goAsync()
        app.container.applicationScope.launch {
            try {
                // 跨天刷新闹钟随事件重排（幂等）
                runCatching { DailyRefreshScheduler.schedule(app) }
                // 刷新小组件：provideGlance 尾部会重排「课程结束」刷新闹钟
                runCatching { WidgetRefresher.refreshAll(app) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // 用户从系统页授权/撤销精确闹钟后，系统发出此广播；据此重排让权限真正生效
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            // 覆盖安装后恢复小组件刷新闹钟（系统发给本包，无需额外权限）
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
