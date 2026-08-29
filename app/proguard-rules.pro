# Symja resolves large parts of its function catalogue reflectively, so the
# shrinker must not strip its expression classes or built-in function tables.
-keep class org.matheclipse.** { *; }
-keep class org.hipparchus.** { *; }
-dontwarn org.matheclipse.**
-dontwarn org.hipparchus.**
-dontwarn com.google.common.**
-dontwarn javax.annotation.**
