# =============================================================================
# R8 / ProGuard 规则
#
# 本项目依赖的反射 / 序列化 / 平台 SDK 需要显式 keep 字段或类，避免 R8 把它们
# 重命名或剔除。其它纯 Kotlin/Compose/Hilt/Room 代码由各自插件自动处理。
# =============================================================================

# 调试 stacktrace 用：保留行号；类名仍混淆
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# =============================================================================
# Kotlinx.serialization
# 序列化插件在编译期生成 $serializer，但反射查找时仍需保留 @Serializable 类
# 的 Companion / 字段；DTO 类的字段名是 JSON key，全部 keep。
# =============================================================================
-keepattributes *Annotation*, InnerClasses

# 保留所有 @Serializable 标记的类的 Companion / $serializer
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static **$* *;
    static <1>$Companion Companion;
}

# 显式 keep 业务 DTO：JSON 键 = 字段名，混淆字段名即破坏序列化
-keep,includedescriptorclasses class com.guiderun.app.data.remote.dto.** { *; }

-dontnote kotlinx.serialization.AnnotationsKt

# =============================================================================
# Retrofit + OkHttp
# Retrofit 通过反射读接口方法/注解；OkHttp 内部反射 Platform。
# =============================================================================
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Retrofit 接口（DI 按类型注入，且方法注解需保留）
-keep interface com.guiderun.app.data.remote.api.** { *; }

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# =============================================================================
# Hilt
# Hilt Gradle plugin 自动注入大部分 keep 规则；这里只兜底入口注解。
# =============================================================================
-keep class dagger.hilt.** { *; }
-keep,allowobfuscation @dagger.hilt.android.AndroidEntryPoint class *
-keep,allowobfuscation @dagger.hilt.android.HiltAndroidApp class *

# =============================================================================
# Room
# KSP 已经生成所需代码；兜底 keep RoomDatabase 子类。
# =============================================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# =============================================================================
# Compose
# Compose Compiler 已正确处理；不需额外规则。Lifecycle/ViewModel 同理。
# =============================================================================

# =============================================================================
# Kotlinx.coroutines
# 协程内部 service-loader 与反射查找需保留。
# =============================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.debug.**

# =============================================================================
# 高德地图 SDK（com.amap.api:3dmap）
# 高德文档要求 keep 整包 + autonavi/loc 子包。
# =============================================================================
-keep class com.amap.api.**{*;}
-keep class com.autonavi.**{*;}
-keep class com.loc.**{*;}
-dontwarn com.amap.api.**

# =============================================================================
# 讯飞 MSC SDK（语音听写 IAT + ASR）
# 讯飞内部反射回调，文档要求 keep 整包。
# =============================================================================
-keep class com.iflytek.**{*;}
-dontwarn com.iflytek.**

# =============================================================================
# Domain 异常 / Sealed 类
# 业务异常通过类型匹配，保留以便 mapException 正确分派。
# =============================================================================
-keep class com.guiderun.app.domain.exception.** { *; }
