/*
 * DailyRefreshScheduler.kt —— 小组件「跨天刷新」的精确闹钟（M6 需求 2）
 *
 * 为何弃用 WorkManager 周期任务：实测其 Job 的 earliest run time 落在次日约 07:33
 * 而非 00:05（周期任务时间按「入队时刻 + 24h」重排，且 Doze 下继续漂移）。
 * 跨天是「必须发生在凌晨」的语义，用 AlarmManager 精确闹钟表达才正确。
 *
 * 闹钟在每次触发后由 receiver 的 onUpdate 重排下一天（链式推进），并在 App 启动 /
 * 系统事件 / 数据变更后重排，保证覆盖安装、强停、重启后都能恢复。
 */
package com.gould.xputimetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

object DailyRefreshScheduler {

    /** 与提醒闹钟(4001)、结束刷新(4002)不冲突的独立 requestCode。 */
    private const val REQUEST_CODE = 4003
    private const val REFRESH_HOUR = 0
    private const val REFRESH_MINUTE = 5

    /** 排定「下一个 00:05」的刷新；幂等（重复调用只覆盖同一 PendingIntent）。 */
    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val triggerAt = nextRefreshEpochMilli()
        // 优先精确，未授权则降级（不精确但可用）
        if (canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(appContext))
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(appContext))
        }
    }

    /**
     * 精确闹钟是否可用。
     *
     * M7：课前提醒移除后，本判断的用途只剩「小组件的跨天/课后刷新精度」——
     * 未授权时上述两条闹钟会降级为不精确（系统窗口最长约 1 小时），
     * 故设置页仍据它给出引导。SCHEDULE_EXACT_ALARM 权限本身保留（本文件与
     * EndOfClassRefreshScheduler 都需要它）。
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java)
        return alarmManager.canScheduleExactAlarms()
    }

    fun cancel(context: Context) {
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent(context.applicationContext))
    }

    /**
     * 现在到下一个 00:05 的 epoch 毫秒（纯计算，抽成 internal 便于单测）。
     * 边界：now == 00:05 整时视为「已过」，返回次日 00:05（避免刚过就立刻又触发）。
     */
    internal fun nextRefreshEpochMilli(now: LocalDateTime = LocalDateTime.now()): Long {
        val todayTarget = now.toLocalDate().atTime(REFRESH_HOUR, REFRESH_MINUTE)
        val target = if (now.isBefore(todayTarget)) todayTarget else todayTarget.plusDays(1)
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** 复用既有小组件 receiver（不新增组件）：触发系统级 APPWIDGET_UPDATE 广播。 */
    private fun pendingIntent(context: Context): PendingIntent {
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TodayWidgetReceiver::class.java))
        val intent = Intent(context, TodayWidgetReceiver::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
