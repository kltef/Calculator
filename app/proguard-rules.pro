# --- Obfuscation -------------------------------------------------------------
#
# Names are kept. Symja and the libraries under it resolve classes and members
# by name at runtime, and renaming them is the most likely cause of the startup
# failure seen when minification was first enabled. Shrinking still happens, and
# shrinking is where nearly all of the size saving comes from - the unminified
# build carries about 80 MB of uncompressed dex, most of it Symja code this app
# never calls.
-dontobfuscate

# --- Symja and its maths stack ----------------------------------------------
#
# Symja registers its whole builtin catalogue through reflection and holds large
# static tables, so it is kept wholesale rather than trimmed member by member.
-keep class org.matheclipse.** { *; }
-keep class org.hipparchus.** { *; }
-keep class org.apfloat.** { *; }
-keep class edu.jas.** { *; }
-dontwarn org.matheclipse.**
-dontwarn org.hipparchus.**
-dontwarn edu.jas.**

# Libraries Symja reaches reflectively or through service lookup. These are the
# ones with no keep rules the first time minification was tried.
-keep class com.esotericsoftware.** { *; }
-keep class org.codehaus.janino.** { *; }
-keep class org.codehaus.commons.** { *; }
-keep class org.jgrapht.** { *; }
-keep class org.chocosolver.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.esotericsoftware.**
-dontwarn org.codehaus.**
-dontwarn org.jgrapht.**
-dontwarn org.chocosolver.**
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.antlr.**
-dontwarn io.pebbletemplates.**
-dontwarn us.hebi.matlab.**
-dontwarn org.ehcache.**

# --- Platform classes Android does not ship ---------------------------------
#
# These are referenced but unreachable here. `dontwarn` silences the build; it
# does not make them exist, which is why SymjaConfiguration also stops the
# catalogue from initialising the parts that need them.
-dontwarn java.lang.management.**
-dontwarn javax.lang.model.**
-dontwarn javax.annotation.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn com.google.common.**
-dontwarn org.osgi.**
-dontwarn sun.misc.**

# --- MediaPipe / ML Kit ------------------------------------------------------
#
# Both cross the JNI boundary and instantiate results reflectively.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.mlkit.**
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- protobuf-lite -----------------------------------------------------------
#
# MediaPipe builds its task graph from protobuf-lite messages, and
# protobuf-lite resolves message fields reflectively through `dynamicMethod`.
# Without these rules the generated classes survive but their field metadata
# does not, and HandLandmarker.createFromOptions fails at runtime with nothing
# useful in the message.
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}
-keep class com.google.flatbuffers.** { *; }
-dontwarn com.google.protobuf.**

# AutoValue-generated classes backing MediaPipe's options objects.
-keep class **.AutoValue_* { *; }

# --- Flogger -----------------------------------------------------------------
#
# MediaPipe logs through Flogger, and `FluentLogger.forEnclosingClass()` finds
# its caller by walking the call stack. R8's optimiser inlines methods and so
# removes the very frames it looks for, and the failure is a bare
# "no caller found on the stack for: com.google.common.flogger.FluentLogger"
# thrown while building the hand landmarker.
#
# `-dontoptimize` is the reliable fix: it turns off the inlining pass while
# leaving shrinking on, and shrinking is where the size saving comes from -
# removing Symja code the app never calls, not rewriting the code it keeps.
-dontoptimize
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**

# Stack-walking needs real frames to report.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod
