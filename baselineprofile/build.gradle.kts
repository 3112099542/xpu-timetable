/*
 * baselineprofile 模块构建文件 —— Baseline Profile 生成器（M7）
 *
 * 它是什么：一个 com.android.test 模块，**只含测试代码**。既不会被编进 App，
 * 也不会出现在发布产物里（settings.gradle.kts 单独 include，不参与 :app 的依赖闭包）。
 *
 * 作用：跑 Macrobenchmark，把"冷启动 → 周视图首屏 → 翻周 → 切底栏"这条真实路径上
 * 实际执行到的类与方法记录下来，由 androidx.baselineprofile 插件合并成
 * app/src/release/generated/baselineProfiles/baseline-prof.txt，
 * 最终以 assets/dexopt/baseline.prof 的形式打进 release APK，安装时即被 ART 采用。
 *
 * 接线依据（2026-09-30 核对官方 configure-baseline-profiles，并用 javap 核实插件真实 API）：
 *   - 生成器模块：com.android.test + androidx.baselineprofile，且必须设 targetProjectPath；
 *   - App 模块：同样应用 androidx.baselineprofile，并声明 baselineProfile(project(":baselineprofile"))；
 *   - 生成器侧的 baselineProfile{} 只暴露 4 个属性：
 *     managedDevices / useConnectedDevices / skipBenchmarksOnEmulator / enableEmulatorDisplay。
 *
 * 注意：AGP 9 内建 Kotlin 支持，**不得**再 apply org.jetbrains.kotlin.android（Spec §4）。
 */
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.gould.xputimetable.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Macrobenchmark 的"自测式"采集要求 API 28+（API 33+ 起无需 root）
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 被测模块：生成的 profile 会写回 app 模块（配合 app 侧的 baselineProfile(project) 依赖）
    targetProjectPath = ":app"

    // 自仪器化：benchmark 库以自身进程驱动被测 App（官方模板要求）
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

// 采集来源：本机开发机已连真机。真机热点路径比模拟器更贴近用户实际；
// 换 managedDevice 时改为 managedDevices = listOf("...")。
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
