/*
 * WeekCalc.kt —— 周次计算的纯函数（无状态、无 Android 依赖、可单测）
 *
 * 作用：把"周次"相关的所有判定逻辑集中到一处纯函数里，便于单元测试，也避免
 * 在 UI / 仓库里散落"今天减开学日再除以 7"之类的重复且易错代码。
 *
 * 为什么独立成文件（架构 §6.2 已批准为架构树之外的新增文件）：周次判定与当前周计算
 * 属于"确定性业务逻辑"，必须能在 JVM 单测里直接验证，不能依赖 Android Context，
 * 所以这里只用 java.time（minSdk 26 原生支持，无需 desugaring）。
 *
 * 关键坑（Spec §11）：开学前打开 App 绝不能出现"负数周次"。currentWeek 在
 * today < startDate 时直接返回 null，由上层显示"未开学"状态，而不是算出一个负数。
 */
package com.gould.xputimetable.domain

import com.gould.xputimetable.domain.model.CourseSession
import com.gould.xputimetable.domain.model.WeekType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 周次计算工具。全部为纯函数：相同输入永远得到相同输出，不读写任何外部状态。
 */
object WeekCalc {

    /**
     * 一周次上限：仅用于防止脏数据（如 endWeek=9999）导致超长循环。
     * 取 60 远大于任何真实学期（一般 16-20 周），不会影响正常数据。
     */
    private const val MAX_WEEKS = 60

    /**
     * 判断某次上课安排在第 [week] 周是否生效。
     *
     * 生效需同时满足：
     *   1. 周次落在 [startWeek, endWeek] 闭区间内；
     *   2. 满足周次类型：ALL 任意周；ODD 为奇数周；EVEN 为偶数周。
     *
     * 与架构 §7.3 的 SQL 过滤条件严格一致：
     *   start_week <= :week AND end_week >= :week
     *   AND (week_type='ALL' OR (ODD AND :week%2=1) OR (EVEN AND :week%2=0))
     */
    fun isSessionActive(
        weekType: WeekType,
        startWeek: Int,
        endWeek: Int,
        week: Int,
    ): Boolean {
        if (week < startWeek || week > endWeek) return false
        return when (weekType) {
            WeekType.ALL -> true
            WeekType.ODD -> week % 2 == 1
            WeekType.EVEN -> week % 2 == 0
        }
    }

    /**
     * 判断某次上课安排在第 [week] 周是否生效（M2-B P0-A 重载：支持显式周次列表）。
     *
     * [weeks] 非空时**只按列表判定**（week 是否出现在列表中），忽略区间与单双周；
     * 为空时沿用旧逻辑（区间 + 单双周）。与 CourseSessionDao 两支 OR 查询严格一致。
     * 旧四参函数保留（供既有测试与兼容调用），二者口径不得漂移。
     */
    fun isSessionActive(
        weeks: List<Int>?,
        weekType: WeekType,
        startWeek: Int,
        endWeek: Int,
        week: Int,
    ): Boolean {
        if (weeks != null) return week in weeks
        return isSessionActive(weekType, startWeek, endWeek, week)
    }

    /**
     * 计算"当前周"：给定学期第一周周一 [startDate] 与今天 [today]。
     *
     * 算法：today 与 startDate 相差的天数整除 7，再加 1（startDate 当天为第 1 周）。
     *   - today 与 startDate 同一周（相差 0~6 天）都算第 1 周；
     *   - today 比 startDate 晚 7~13 天算第 2 周，依此类推。
     *
     * 关键约束（Spec §11 已知坑）：若 [today] 早于 [startDate]（开学前），
     * 返回 null，绝不允许返回负数周次。上层据此显示"未开学"。
     *
     * 注意：本函数不知道学期总周数，超期（已放假）也照常返回数字；
     * 是否需要钳制到 totalWeeks 由上层根据 Term.totalWeeks 决定。
     */
    fun currentWeek(startDate: LocalDate, today: LocalDate): Int? {
        if (today.isBefore(startDate)) return null
        val days = ChronoUnit.DAYS.between(startDate, today)
        return (days / 7).toInt() + 1
    }

    /**
     * 给定周次算该周周一日期；第 [week] 周的周一 = startDate + (week - 1) * 7 天。
     * week 必须 >= 1；传入 < 1 会按 1 处理（等价于返回 startDate），避免产生早于开学日的日期。
     */
    fun mondayOfWeek(startDate: LocalDate, week: Int): LocalDate {
        val safeWeek = if (week < 1) 1 else week
        return startDate.plusDays(((safeWeek - 1) * 7).toLong())
    }

    /**
     * 解析数据库中的 week_type 字符串（脏数据容错）。
     *
     * 不变量：**绝不抛异常**。空串、空白、拼写错误或未知 token 一律回退 ALL
     * （按"每周都上"处理，至少不会让课程凭空消失；这类行的真实值仍可通过 edited_at 等溯源）。
     * 匹配时忽略大小写与首尾空白，兼容 "odd" / " ODD " 等写法。
     */
    fun parseWeekType(raw: String?): WeekType {
        val normalized = raw?.trim()?.uppercase().orEmpty()
        return when (normalized) {
            "ODD" -> WeekType.ODD
            "EVEN" -> WeekType.EVEN
            else -> WeekType.ALL
        }
    }

    /**
     * 某条上课安排"下一次"开始的绝对时间（epoch 毫秒）；若本学期已无该安排，返回 null。
     *
     * 语义："下一次还没结束"的一节——即结束时间晚于 [fromEpochMilli] 的最近一次。
     * 跨天、跨周、单双周由本函数统一处理，调用方只负责在多条安排里取最小值（择优）。
     *
     * 为什么用注入的时间与时区：便于在 JVM 单测里构造确定场景，不依赖系统时钟。
     *
     * @param session        上课安排
     * @param startMinute    该安排起始节次的开始分钟数（来自 time_slots）；null 表示作息缺失
     * @param endMinute      该安排结束节次的结束分钟数；null 表示作息缺失
     * @param termStart      学期第一周周一
     * @param fromEpochMilli 基准时刻
     * @param zoneId         时区
     */
    fun nextSessionTime(
        session: CourseSession,
        startMinute: Int?,
        endMinute: Int?,
        termStart: LocalDate,
        fromEpochMilli: Long,
        zoneId: ZoneId,
    ): Long? {
        // 脏数据边界（数据可能来自导入或手工改库）：任何一项不合法都直接返回 null，
        // 绝不让越界值进入时间计算或造成超长循环（例如 endWeek = 9999 会扫 7 万天）
        if (startMinute == null || endMinute == null) return null
        if (startMinute !in 0..1439 || endMinute !in 0..1439) return null
        if (endMinute <= startMinute) return null
        if (session.dayOfWeek !in 1..7) return null
        // 矛盾区间（起始周晚于结束周）属于无法判断的脏数据：直接放弃计算，
        // 而不是把 startWeek 夹进 endWeek 范围内"悄悄修好"（那会凭空造出一次不存在的课）
        if (session.startWeek > session.endWeek) return null
        val safeEndWeek = session.endWeek.coerceIn(1, MAX_WEEKS)
        val safeStartWeek = session.startWeek.coerceIn(1, safeEndWeek)
        val fromDate = Instant.ofEpochMilli(fromEpochMilli).atZone(zoneId).toLocalDate()
        val maxOffsetDays = 7L * (safeEndWeek + 1)
        for (offset in 0..maxOffsetDays) {
            val date = fromDate.plusDays(offset)
            if (date.dayOfWeek.value != session.dayOfWeek) continue
            val week = currentWeek(termStart, date) ?: continue
            if (!isSessionActive(session.weeks, session.weekType, safeStartWeek, safeEndWeek, week)) continue
            val dayStart = date.atStartOfDay(zoneId)
            val startEpoch = dayStart.plusMinutes(startMinute.toLong()).toInstant().toEpochMilli()
            val endEpoch = dayStart.plusMinutes(endMinute.toLong()).toInstant().toEpochMilli()
            if (endEpoch > fromEpochMilli) return startEpoch
        }
        return null
    }

    /**
     * 按开学日推定学期名（M9：从 TimetableViewModel 提到此处共用 —— 创建学期现在有
     * 「课表页空态」与「学期设置页」两个入口，命名规则必须同源）。
     *
     * 规则：9 月及以后开学算秋季第一学期（`YYYY-YYYY+1-1`），否则算春季第二学期
     * （`YYYY-1-YYYY-2`）。纯函数，便于单测。
     */
    fun termNameOf(startDate: LocalDate): String {
        val year = startDate.year
        return if (startDate.monthValue >= 9) "$year-${year + 1}-1" else "${year - 1}-$year-2"
    }
}
