/*
 * TodayWidgetReceiver.kt —— 小组件接收器（M3；M6 需求 2 增补 onUpdate）
 *
 * Glance 默认实现负责 onUpdate（添加 / 尺寸变化 / 系统要求更新）时回调
 * TodayWidget.provideGlance 重绘（其中会顺带重排「课程结束」刷新闹钟，勿手动重复）。
 * M6 起在 onUpdate 里额外重排跨天精确闹钟——每次系统触发小组件更新都把
 * 「下一个 00:05」钉回最新，是闹钟链式推进的关键一环。
 * 选择器里显示的名字来自 Manifest 中 receiver 的 android:label（@string/widget_label）。
 */
package com.gould.xputimetable.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // 只做跨天闹钟重排；不手动触发刷新（super 已触发 provideGlance，见文件头注释）
        runCatching { DailyRefreshScheduler.schedule(context) }
    }
}
