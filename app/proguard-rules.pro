-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

-keep class sc.pirate.app.api.model.** { *; }

-keep class io.privy.** { *; }
-dontwarn io.privy.**

-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

-dontwarn org.web3j.**
-keep class org.web3j.** { *; }

-dontwarn org.xmtp.**
-keep class org.xmtp.** { *; }

-keep class com.sun.jna.** { *; }
-keepclassmembers class com.sun.jna.** { native <methods>; *; }
-keep class uniffi.** { *; }
-keepclassmembers class ** { public *; protected *; }
-dontwarn uniffi.**
-dontwarn com.sun.jna.**

# Optional transitive APIs referenced by crypto/RPC dependencies but not packaged
# in the Android app runtime.
-dontwarn com.squareup.okhttp.**
-dontwarn groovy.lang.**
-dontwarn io.netty.**
-dontwarn io.vertx.**
-dontwarn java.beans.**
-dontwarn javax.naming.**
-dontwarn sun.nio.ch.DirectBuffer
