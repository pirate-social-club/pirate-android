-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

-keep class sc.pirate.app.api.model.** { *; }

-keep class io.privy.** { *; }
-dontwarn io.privy.**

-keep class com.sun.jna.** { *; }
-keepclassmembers class com.sun.jna.** { native <methods>; *; }
-keep class uniffi.** { *; }
-keepclassmembers class ** { public *; protected *; }
-dontwarn uniffi.**
-dontwarn com.sun.jna.**
