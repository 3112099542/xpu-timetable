/*
 * UiPrefs.kt —— 界面级偏好（DataStore Preferences）
 *
 * 作用：存"跟课程数据无关、只影响展示"的开关（当前只有「显示老师姓名」）。
 *
 * 为什么单独建而不是塞进 Room：
 *   这类开关没有查询需求，放进 courses 表要为每个用户造一行默认值；放进 DataStore
 *   只是本地一个 key，读写都廉价。这与既有的"数据归 Room、界面偏好归 DataStore"分层一致。
 *
 * 为什么不用 SharedPreferences：
 *   项目已引入 androidx.datastore（见 libs.versions.toml），DataStore 的读写在
 *   Dispatchers.IO 上、且提供 Flow，避免 SharedPreferences 的同步读主线程卡顿旧问题。
 *
 * 默认值策略：所有 key 都在读取侧兜默认值（it[key] ?: DEFAULT），
 * 这样"从未写入过"与"用户显式关掉"在存储层可区分，而读侧永远拿到可用值也更省心。
 */
package com.gould.xputimetable.data.prefs

import android.content.Context

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 存储名（文件名 ui_prefs.pb）。改名字等于丢掉用户既有偏好，不要随意动。 */
private const val STORE_NAME = "ui_prefs"

/** 偏好键集中在这里，避免散落字符串。 */
private object Keys {
    /** 课程卡是否显示「教师名」。默认**开**（老用户从来没关过，行为不该变）。 */
    val SHOW_TEACHER = booleanPreferencesKey("show_teacher")
}

/** 默认显示老师（界面偏好默认值，也用作 Flow 起步的初值）。 */
const val DEFAULT_SHOW_TEACHER = true

/**
 * Context 上的 DataStore 持有者。
 *
 * 用 `preferencesDataStore` 委托而不是每次 `createDataStore`：委托内部按 Context 缓存实例，
 * 反复 new DataStore 会各自开文件流、甚至互相覆盖写，是 DataStore 最常见的翻车点。
 */
private val Context.uiPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = STORE_NAME,
)

/** 界面偏好的读写入口（构造注入，便于测试替身）。 */
class UiPrefs(
    private val dataStore: DataStore<Preferences>,
) {

    /** 「显示老师姓名」当前值（Storage 里没有时给默认值）。 */
    val showTeacher: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[Keys.SHOW_TEACHER] ?: DEFAULT_SHOW_TEACHER }

    /** 写入「显示老师姓名」。失败向外抛——调用方是按钮点击，上层已有 Snackbar 兜底。 */
    suspend fun setShowTeacher(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.SHOW_TEACHER] = value }
    }

    companion object {
        /** 方便调用方创建实例（把 Context 扩展藏在内部）。 */
        fun create(context: Context): UiPrefs = UiPrefs(context.uiPrefsDataStore)
    }
}
