# quest fork: Meta Spatial SDK toolkit/vr AARs ship no consumer rules; the SDK uses JNI and reflection.
-keep class com.meta.spatial.** { *; }
-dontwarn com.meta.spatial.**

# quest fork (S4): ML Kit creates its component registrars by reflection; R8 full mode strips
# their no-arg constructors otherwise ("Could not instantiate ...Registrar").
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-dontwarn com.google.mlkit.**
