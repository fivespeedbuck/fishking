---
status: accepted
---

# 采用分功能模块和版本化本地数据库

咸鱼大王需要让待办、打卡、日记和人生目标共享事实，同时长期保留循环实例、撤销、媒体和关联历史。正式工程采用 `app` 壳、`core:model`、`core:database`、`core:ui`、`core:usecase` 与四个 `feature` 模块；数据使用 Room 管理的版本化 SQLite，页面只通过仓储与用例访问，不继续把 UI、业务和 JSON 存储堆进单个 Activity。

选择 Room 是因为当前产品已经需要稳定主键、关系查询、Flow 实时同步、原子事务、schema 导出与迁移验证。DaveList 旧 Java/Canvas 与 `SharedPreferences + JSON` 只作为视觉和行为参考，不进入正式数据路径。

依赖方向固定为：`app -> feature/core`、`feature -> core:ui + core:usecase + core:model`、`core:usecase -> core:database + core:model`、`core:database -> core:model`、`core:ui -> core:model`。feature 之间不直接依赖，feature 也不直接依赖 `core:database`。
