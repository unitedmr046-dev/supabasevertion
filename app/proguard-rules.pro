# Keep line numbers & source file for Crashlytics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Data models & Firestore POJOs
-keep class com.iyonzkasir.data.** { *; }
-keepclassmembers class com.iyonzkasir.data.** { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
