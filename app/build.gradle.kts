import java.net.URI
import java.util.Properties
import org.gradle.api.GradleException

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
}

val localProps = Properties().apply {
  val propsFile = rootProject.file("local.properties")
  if (propsFile.exists()) {
    propsFile.inputStream().use(::load)
  }
}

fun localProp(name: String): String? =
  localProps.getProperty(name)?.trim()?.takeIf { it.isNotBlank() }

fun envProp(name: String): String? =
  System.getenv(name)?.trim()?.takeIf { it.isNotBlank() }

fun gradleProp(name: String): String? =
  (findProperty(name) as? String)?.trim()?.takeIf { it.isNotBlank() }

fun runtimeProp(name: String): String? =
  gradleProp(name) ?: localProp(name) ?: envProp(name)

fun buildConfigString(value: String): String =
  "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
  namespace = "sc.pirate.app"
  compileSdk = 36

  val signingProps = Properties().apply {
    val propsFile = rootProject.file("signing.properties")
    if (propsFile.exists()) {
      propsFile.inputStream().use(::load)
    }
  }

  fun signingProp(name: String): String? =
    signingProps.getProperty(name)
      ?.trim()
      ?.takeIf { it.isNotBlank() }

  signingConfigs {
    val storeFilePath = signingProp("storeFile")
    if (storeFilePath != null) {
      create("release") {
        storeFile = file(storeFilePath)
        storePassword = signingProp("storePassword")
        keyAlias = signingProp("keyAlias")
        keyPassword = signingProp("keyPassword")
      }
    }
  }

  defaultConfig {
    applicationId = "sc.pirate.mobile"
    minSdk = 28
    targetSdk = 36
    versionCode = 9
    versionName = "0.1.0-alpha.7-play-20260513"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    manifestPlaceholders["appLabel"] = "@string/app_name"

    val apiBaseUrl = runtimeProp("API_BASE_URL") ?: "https://api.pirate.sc"
    buildConfigField("String", "API_BASE_URL", buildConfigString(apiBaseUrl))

    val webBaseUrl = runtimeProp("WEB_BASE_URL") ?: "https://pirate.sc"
    buildConfigField("String", "WEB_BASE_URL", buildConfigString(webBaseUrl))

    val xmtpEnvironment = runtimeProp("XMTP_ENVIRONMENT") ?: "production"
    buildConfigField("String", "XMTP_ENVIRONMENT", buildConfigString(xmtpEnvironment))

    val privyEnabled = runtimeProp("PRIVY_ENABLED")
      ?.lowercase()
      ?.let { it == "true" || it == "1" }
      ?: true
    buildConfigField("boolean", "PRIVY_ENABLED", privyEnabled.toString())

    val privyAppId = runtimeProp("PRIVY_APP_ID") ?: "cmnbdx9xk00ty0clapn2q8pdj"
    buildConfigField("String", "PRIVY_APP_ID", buildConfigString(privyAppId))

    val privyAppClientId = runtimeProp("PRIVY_APP_CLIENT_ID") ?: "client-WY6Xkpp2wLef8Y9cWBrZ1GhnmqAtnVh9YisfZ2dA3c7DW"
    buildConfigField("String", "PRIVY_APP_CLIENT_ID", buildConfigString(privyAppClientId))

    val privyRedirectScheme = runtimeProp("PRIVY_REDIRECT_SCHEME") ?: "pirate"
    buildConfigField("String", "PRIVY_REDIRECT_SCHEME", buildConfigString(privyRedirectScheme))
    manifestPlaceholders["privyRedirectScheme"] = privyRedirectScheme

    val reownProjectId = runtimeProp("REOWN_PROJECT_ID") ?: "08db15bf8bdb09d1cbc714f4c39d11a8"
    buildConfigField("String", "REOWN_PROJECT_ID", buildConfigString(reownProjectId))

    val reownRedirectUri = runtimeProp("REOWN_REDIRECT_URI") ?: "pirate://wallet-connect"
    buildConfigField("String", "REOWN_REDIRECT_URI", buildConfigString(reownRedirectUri))
    val parsedReownRedirectUri = try {
      URI(reownRedirectUri)
    } catch (error: Exception) {
      throw GradleException("REOWN_REDIRECT_URI must be a valid URI. Received: $reownRedirectUri", error)
    }
    manifestPlaceholders["reownRedirectScheme"] = parsedReownRedirectUri.scheme ?: "pirate"
    manifestPlaceholders["reownRedirectHost"] = parsedReownRedirectUri.host ?: "wallet-connect"

    val verySdkKey = runtimeProp("VERY_SDK_KEY") ?: ""
    buildConfigField("String", "VERY_SDK_KEY", buildConfigString(verySdkKey))
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.findByName("release")
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
    debug {
      isMinifyEnabled = false
      if (runtimeProp("DEBUG_USE_RELEASE_APPLICATION_ID")?.lowercase() != "true") {
        applicationIdSuffix = ".blacksmith"
      }
      if (runtimeProp("DEBUG_SIGN_WITH_RELEASE")?.lowercase() == "true") {
        signingConfig = signingConfigs.findByName("release")
      }
      manifestPlaceholders["appLabel"] = "Pirate Blacksmith"
      versionNameSuffix = "-blacksmith"
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "/META-INF/DISCLAIMER"
    }
    jniLibs {
      useLegacyPackaging = true
    }
  }
}

dependencies {
  implementation(platform("com.reown:android-bom:1.6.10"))
  implementation(platform("androidx.compose:compose-bom:2026.02.00"))
  implementation("androidx.activity:activity-compose:1.12.4")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("com.adamglin:phosphor-icon:1.0.0")
  implementation("androidx.navigation:navigation-compose:2.9.7")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
  // LocalLifecycleOwner for the video pager: the compose-ui one is deprecated in favour of this.
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
  implementation("androidx.appcompat:appcompat:1.7.1")

  implementation("androidx.credentials:credentials:1.5.0")
  implementation("androidx.credentials:credentials-play-services-auth:1.5.0")

  implementation("io.privy:privy-core:0.9.2")
  implementation("com.reown:android-core")
  implementation("com.reown:appkit")
  implementation("com.google.accompanist:accompanist-navigation-material:0.36.0")

  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
  implementation("org.bouncycastle:bcprov-jdk18on:1.83")
  implementation("org.web3j:abi:4.12.2")
  implementation("org.web3j:crypto:4.12.2")
  implementation("org.xmtp:android:4.9.0")
  implementation("io.agora.rtc:full-sdk:4.6.3")
  implementation("org.very:sdk:1.0.29") {
    exclude(group = "com.caverock", module = "androidsvg")
  }

  implementation("io.coil-kt:coil-compose:2.6.0")
  implementation("io.coil-kt:coil-svg:2.6.0")
  implementation("androidx.media3:media3-exoplayer:1.5.1")
  implementation("androidx.media3:media3-ui:1.5.1")

  implementation("androidx.datastore:datastore-preferences:1.1.4")

  testImplementation("junit:junit:4.13.2")
  debugImplementation("androidx.compose.ui:ui-tooling")
}
