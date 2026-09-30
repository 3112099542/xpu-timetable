# R8 / ProGuard 规则（Release 构建）
#
# 原则：只加必要的 keep 规则，其余交给 R8 自动精简（体积目标 ≤15MB，架构 §4.5）。
# 骨架阶段无需额外规则；以下为后续里程碑的预置说明，规则文本保持注释状态避免误导。

# --- 里程碑 M1 起需要关注的规则（届时按实际报错启用）---
# Room 3：实体类与 DAO 由注解处理器生成，R8 通常无需额外规则；
#         若出现查询方法被裁剪，再为 repository / dao 包添加 keep。
# --- M2-B 起启用的规则（kotlinx-serialization 已接线，release 已开 minify）---
# kotlinx.serialization：保留 @Serializable 生成的序列化器（官方建议规则，
# 未 keep 时 R8 会裁剪 serializer() 合成方法，release 下解析直接抛异常）。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gould.xputimetable.**$$serializer { *; }
-keepclassmembers class com.gould.xputimetable.** {
    *** Companion;
}
-keepclasseswithmembers class com.gould.xputimetable.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WebView JS 注入接口（策略 B）：通过 @JavascriptInterface 暴露的方法必须 keep。
