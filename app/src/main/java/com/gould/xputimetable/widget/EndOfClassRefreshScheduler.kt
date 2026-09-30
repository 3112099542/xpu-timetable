/*
 * EndOfClassRefreshScheduler.kt —— 「课程结束时刻」小组件精确刷新（M4-W R-逐节推进）
 *
 * 现有 5 类刷新触发源均不覆盖课程结束时刻；本调度器在每次小组件取数完成后，
 * 把「下一门未结束课程的结束时刻」挂为一次性精确闹钟（setExactAndAllowWhileIdle），
 * 触发后广播 APPWIDGET_UPDATE 到既有 TodayWidgetReceiver → 重跑取数 → 已结束课程
 * 从列表消失、下一节上移，并在 provideGlance 尾部重排再下一门（链式推进）。
 * 时刻已过（毫秒级漂移）则直接立即刷新；null 则取消（今天再无未结束课程）。
 */
package com.gould.xputimetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object EndOfClassRefreshScheduler {

    /** 与提醒闹钟(4001)不冲突的独立 PendingIntent；重复安排即覆盖，幂等。 */
    private const val REQUEST_CODE = 4002

    /** 安排 [nextEndEpochMilli] 时刻精确刷新；null = 取消；已过 = 立即刷新。 */
    suspend fun schedule(context: Context, nextEndEpochMilli: Long?) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        if (nextEndEpochMilli == null) {
            alarmManager.cancel(pendingRefresh(appContext))
            return
        }
        if (nextEndEpochMilli <= System.currentTimeMillis()) {
            // 课程刚结束：直接立即刷新（刷新内部会重排下一门），不再挂闹钟
            WidgetRefresher.refreshAll(appContext)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextEndEpochMilli,
            pendingRefresh(appContext),
        )
    }

    /** 复用既有 receiver（不新增）：APPWIDGET_UPDATE + 桌面实例 ids。 */
    private fun pendingRefresh(context: Context): PendingIntent {
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
