# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Gson
-keep class com.musicapp.data.remote.dto.** { *; }
-keep class com.google.gson.** { *; }

# Room
-keep class com.musicapp.data.local.entity.** { *; }