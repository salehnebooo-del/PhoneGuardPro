# PHONE GUARD PRO
# No app classes are accessed by reflection. The only reflection target is the
# framework class com.android.internal.os.PowerProfile, which R8 does not touch.
-dontwarn org.jetbrains.annotations.**
