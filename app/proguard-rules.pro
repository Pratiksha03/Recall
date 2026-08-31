# zstd-jni's native library looks its own classes and fields up by name through JNI,
# so R8 must not rename or drop them. Without this, reading a .colpkg crashes in a
# release build only — the worst kind of bug to find late.
-keep class com.github.luben.zstd.** { *; }
