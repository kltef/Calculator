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
