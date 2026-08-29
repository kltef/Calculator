# Symja resolves large parts of its function catalogue reflectively, so the
# shrinker must not strip its expression classes or built-in function tables.
-keep class org.matheclipse.** { *; }
-keep class org.hipparchus.** { *; }
-dontwarn org.matheclipse.**
-dontwarn org.hipparchus.**

# Symja's arbitrary-precision backend (apfloat) sizes its cache from the JVM
# management API, which does not exist on Android. It falls back to defaults.
-dontwarn java.lang.management.**

# Guava compiles against the annotation-processing API for its @Weak/@Nullable
# style annotations; none of it is reachable at runtime.
-dontwarn javax.lang.model.**
-dontwarn javax.annotation.**
-dontwarn com.google.common.**

# jgrapht and friends ship optional OSGi service lookup that Android never uses.
-dontwarn org.osgi.**
