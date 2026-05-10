# ProGuard rules for TubeShield

# Keep AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Keep application classes
-keep class com.tubesheild.** { *; }
-keep interface com.tubesheild.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep View constructors for inflation from XML
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep WebView
-keep class android.webkit.** { *; }
-keep interface android.webkit.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Kotlin metadata
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**
