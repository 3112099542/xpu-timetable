# M2-D-fix 任务规格：学期起始日保存失败（UNIQUE constraint failed: terms.id）

> 症状（真机实测，App 自己弹出的报错）：设置页点「保存学期」→ Snackbar
> **「保存学期失败：Error code: 1555, message: UNIQUE constraint failed: terms.id」**
>
> 影响：**P0 功能级 —— 「学期起始日 / 总周数校正」从来无法保存**。
> 而这个起始日正是**提醒**与**小组件「今天」**计算周次的唯一依据（M2-D 规格里加这一节，就是因为它决定提醒正确性）。
> 也就是说：用户一旦开学日填错，既不能自己改，也没有别的地方能改 → 提醒/小组件会一直按错的周次算。

---

## 1. 复现（我实测的完整路径）

1. 打开 App → 周视图 → 右上齿轮「设置」
2. 直接点「**保存学期**」（**改不改日期都一样**，因为报错与"值是否变化"无关）
3. 弹出：`保存学期失败：Error code: 1555, message: UNIQUE constraint failed: terms.id`
4. 数据库核对：`terms.start_date` 仍是旧值 → **确实没写入**

## 2. 根因（精确到行）

`app/src/main/java/com/gould/xputimetable/data/repository/TimetableRepositoryImpl.kt:69`

```kotlin
override suspend fun upsertTerm(term: Term): Long {
    val hasActive = termDao.observeActive().first() != null
    val toSave = if (hasActive) term else term.copy(isActive = true)
    return termDao.insert(toSave.toEntity())        // ✗ 更新场景也走 INSERT
}
```

`TermDao.insert` 是**裸 `@Insert`**（`app/src/main/java/com/gould/xputimetable/data/db/dao/TermDao.kt:20`）：

```kotlin
@Insert
suspend fun insert(term: TermEntity): Long
```

Room 的 `@Insert` 默认冲突策略是 **ABORT** → 当传入的学期**已存在（id=1）**时，主键冲突 → SQLite 错误码 **1555**（UNIQUE constraint failed）。
方法名叫 `upsertTerm`，但实现只做了"insert"那一半。

## 3. 修复（推荐方案 A：最小改动，不需要新增任何 DAO 方法）

`TermDao` **已经有** `@Update suspend fun update(term: TermEntity)`（:23）与
`@Query("SELECT * FROM terms WHERE id = :id") suspend fun getById(id: Long): TermEntity?`（:29）→ 直接用：

```kotlin
override suspend fun upsertTerm(term: Term): Long {
    val hasActive = termDao.observeActive().first() != null
    val toSave = if (hasActive) term else term.copy(isActive = true)
    val entity = toSave.toEntity()
    val exists = entity.id != 0L && termDao.getById(entity.id) != null
    return if (exists) {
        termDao.update(entity)      // ✓ UPDATE：不再撞主键，也不会触发级联
        entity.id
    } else {
        termDao.insert(entity)      // 新增学期（id=0）仍走 insert
    }
}
```

- 新增学期（`TimetableViewModel` 首次创建学期，id=0）→ 走 insert ✓ 行为不变。
- 更新已有学期（设置页保存、校正起始日/总周数）→ 走 update ✓ 修好。
- **不改表结构**：`@Update`/SQL 变化不影响 schema 的 `identityHash` → **无需升 `AppDatabase.version`、无需迁移**。

### 3.1 ⚠️ 严禁的修法（我已用夹具实测后果）

**不要**用 `@Insert(onConflict = OnConflictStrategy.REPLACE)`，也不要用 `INSERT OR REPLACE`：

`courses.term_id` 是 `FOREIGN KEY(term_id) REFERENCES terms(id) ON DELETE CASCADE`
（见 `app/schemas/.../2.json` 的 courses 外键定义）。

`REPLACE` 的语义是**删掉旧行再插新行** → 触发级联 → **该学期的整个课表被清空**。

夹具实测（按项目 schema JSON 的真实 DDL 建表并开启外键，插 1 门课）：

| 修法 | terms 行数 | courses 行数 | 结论 |
|------|-----------|-------------|------|
| `UPDATE terms SET start_date=... WHERE id=1` | 1 | **1** | ✓ 安全 |
| `INSERT OR REPLACE INTO terms(id,...) VALUES(1,...)` | 1 | **0** | ✗ **课表被级联删除** |

（另：Room 的 `@Upsert` 也可以——它是"先 INSERT、冲突时改走 UPDATE"，不是 REPLACE，不会级联；但方案 A 更直观，推荐 A。）

## 4. 交付纪律

- 只改 `TimetableRepositoryImpl.upsertTerm`（如需，可动 `TermDao`，但**不要**加 REPLACE 冲突策略）
- 不加依赖、不改表结构、不动提醒/小组件/导入链路；单文件 ≤ 300 行
- 沙箱内无 git，不要 commit；**不要碰 adb / 不要装机**（真机验证由我方执行）

## 5. 构建与自测

```
export JAVA_HOME=/home/othc3/opt/jdk-21b
cd /home/othc3/WorkBuddy/安卓软件开发
/home/othc3/opt/gradle-9.7.1/bin/gradle assembleDebug testDebugUnitTest --console=plain > /tmp/termfix.log 2>&1
echo "EXIT=$?" >> /tmp/termfix.log
grep -E "^EXIT=|BUILD (SUCCESSFUL|FAILED)" /tmp/termfix.log | head -3
```
（**禁止**管道调用 Gradle。）用例基线 **205**，不得减少。

## 6. 回传格式

1. 两条命令的 EXIT 码与 BUILD 结果；用例总数与失败数
2. `upsertTerm` 改后全文（原样贴出）+ 文件行数
3. 自证：`grep -rn "OnConflictStrategy.REPLACE\|INSERT OR REPLACE" app/src/main` 输出应为空
4. 未验证项如实列出

## 7. 我方真机验证（修复后执行，也是 AC-19 的补验路径）

1. 设置页 → 起始日改到**未来某周一**（如 2026-09-21）→ 保存 → **Snackbar 不再报错**
2. `run-as` 读库：`terms.start_date` 确实变为新值 ✓
3. 回桌面看小组件：应显示「**今天没有课**」（本周不在学期周内）→ **补验 AC-19**
4. `dumpsys alarm`：提醒闹钟随周次变化重排（起始日在未来 → 无下一节课时应被取消；恢复后应回到周五 07:45）
5. 把起始日改回 `2026-09-14` → 再保存一次（**第二次保存同样必须成功**，验证 update 路径可重复）
6. 数据核对：`courses` 必须仍是 `MANUAL 1 + WEB 9`、`course_sessions` 17 行（**验证没有被 REPLACE 级联清空**）
