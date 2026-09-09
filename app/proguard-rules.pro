# ---- Hilt ----
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**

# ---- Retrofit ----
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Gson（凡是用 Gson 反射序列化的模型必须 keep 字段名）----
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# 项目内 Gson 模型：汇率响应 / 密码箱条目 / 旧版备份 DTO / 经期存储 DTO
-keep class com.flechazo.toolbox.feature.currency.RateResponse { *; }
-keep class com.flechazo.toolbox.feature.password_vault.VaultEntry { *; }
-keep class com.flechazo.toolbox.core.data.LegacyCountdown { *; }
-keep class com.flechazo.toolbox.core.data.LegacyPassword { *; }
-keep class com.flechazo.toolbox.core.data.LegacyBackup { *; }
# 经期记录的磁盘格式依赖 DTO 字段名；混淆字段名会让已存数据读不出来（静默丢数据）
-keep class com.flechazo.toolbox.feature.period.PeriodCodec$* { *; }
-dontwarn com.google.gson.**

# ---- ZXing ----
-keep class com.google.zxing.** { *; }

# ---- Compose ----
-dontwarn org.jetbrains.annotations.**
