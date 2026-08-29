// Intentionally no `plugins {}` block.
//
// Declaring a plugin here - even with `apply false` - puts it on the root
// classpath without a version, which makes a subproject's versioned request
// for the same artifact fail (kotlin-jvm and kotlin-android ship in one jar).
// Each module resolves its own plugins from gradle/libs.versions.toml instead,
// which also keeps the Android plugin out of the build entirely when :app is
// not included.
