# Room schema 导出目录（架构 §7.4：必须入库）

本目录由 `room3 { schemaDirectory(...) }` 在构建时自动导出，文件名 = 数据库版本号。

## 版本历史

| 文件 | 版本 | 说明 |
|------|------|------|
| `2.json` | v2 | **当前基线**：`course_sessions` 新增 `week_list`（显式周次，教务直连真实数据需要） |

## 为什么没有 v1.json（如实记录）

v1（M1 初版：courses / course_sessions / terms / time_slots / import_logs，`course_sessions` 无 `week_list`）
的导出文件在 2026-09-17 的真机排障过程中被误删，**未能恢复**：

- Room 的 `identityHash` 由内部算法生成，**无法手工重算**，因此不能用"去掉列再导出"的方式伪造；
- 构建产物与 APK 资产中的副本均已随重新构建被覆盖。

**影响与缓解**：

- 运行时**无影响**：设备升级依赖的是 `version` 号 + `Migrations.kt` 里的迁移代码，与 JSON 无关；
  该迁移已在真机（安卓真机 + 旧库）实测通过，用户数据完整保留；
- **仅影响**：未来若要引入 MigrationTestHelper 类的迁移自动化测试，需要 v1 的 JSON 作为起点；
  届时的替代做法是：用 v1 对应的实体定义在测试中新建库，或直接从真机导出一份 v1 库作为夹具。
- **纪律**：调试时不要删除本目录下的文件；确需临时移走，先备份到项目外并在 memory 里记一条。
