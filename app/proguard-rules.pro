-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

-keep class sc.pirate.app.api.model.** { *; }

-keep class io.privy.** { *; }
-dontwarn io.privy.**
