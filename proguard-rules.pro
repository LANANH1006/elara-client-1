# Làm khó đọc code (obfuscation) cho Elara Client

-dontshrink
-dontoptimize
-ignorewarnings
-dontnote **
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-libraryjars <java.home>/jmods/java.base.jmod(!**.jar;!module-info.class)

# Điểm vào Fabric và các class mixin phải giữ tên, nếu không game sẽ không tìm thấy
-keep class com.elara.client.ElaraClient { *; }
-keep class com.elara.client.mixin.** { *; }

# Gson đọc theo tên trường; enum dùng tên trong file config
-keep class com.elara.client.core.ElaraConfig$Data { *; }
-keep class com.elara.client.core.LagFix$Backup { *; }
-keep enum com.elara.client.** { *; }

# Giữ tên thành viên để override của Minecraft vẫn chạy; chỉ đổi tên class
-keepclassmembers class com.elara.client.** { *; }
