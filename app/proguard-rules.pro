# Keep AndroidX
-keep class androidx.** { *; }

# Keep application classes
-keep class com.tubesheild.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
