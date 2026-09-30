pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// 工程名与模块声明。MVP 采用单模块 + 分层分包（ADR-007）。
// M7：新增 :baselineprofile（com.android.test 模块，只含 Baseline Profile 生成器，
// 不参与打进 APK，也不出现在发布产物里）。
rootProject.name = "XpuTimetable"
include(":app")
include(":baselineprofile")
