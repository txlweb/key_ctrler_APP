# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# 激进优化配置
-optimizationpasses 7
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# 移除调试信息以减少体积
# -keepattributes SourceFile,LineNumberTable
# -renamesourcefileattribute SourceFile

# 保留注解
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# 保留Android基础组件
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# 保留View构造函数
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# 保留Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Kotlin相关（精简版）
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
# 只保留必要的Kotlin元数据
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations

# AndroidX相关（精简版）
-dontwarn androidx.**
# 只保留实际使用的AndroidX组件
-keep class androidx.core.** { *; }
-keep class androidx.appcompat.** { *; }
-keep class com.google.android.material.** { *; }

# 移除日志和调试代码
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# 移除System.out.println
-assumenosideeffects class java.lang.System {
    public static void out.println(...);
    public static void err.println(...);
}

# 激进的代码压缩
-allowaccessmodification
-repackageclasses ''
-flattenpackagehierarchy

# 移除未使用的资源引用
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 保留应用特定的类（根据实际需要调整）
-keep class com.idlike.kctrl.mgr.MainActivity { *; }
-keep class com.idlike.kctrl.mgr.FloatingWindowService { *; }
-keep class com.idlike.kctrl.mgr.FloatingWindowManager { *; }

# 移除未使用的本地方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}