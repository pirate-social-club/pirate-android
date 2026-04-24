import java.util.Properties

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

fun runtimeProp(name: String): String? =
  localProp(name) ?: envProp(name)

fun buildConfigString(value: String): String =
  "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
  namespace = "sc.pirate.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "sc.pirate.app"
    minSdk = 28
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0-alpha.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val apiBaseUrl = runtimeProp("API_BASE_URL") ?: "https://api.pirate.sc"
    buildConfigField("String", "API_BASE_URL", buildConfigString(apiBaseUrl))

    val privyAppId = runtimeProp("PRIVY_APP_ID") ?: ""
    buildConfigField("String", "PRIVY_APP_ID", buildConfigString(privyAppId))

    val privyAppClientId = runtimeProp("PRIVY_APP_CLIENT_ID") ?: ""
    buildConfigField("String", "PRIVY_APP_CLIENT_ID", buildConfigString(privyAppClientId))
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
    debug {
      isMinifyEnabled = false
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
    }
  }
}

dependencies {
  implementation(platform("androidx.compose:compose-bom:2026.02.00"))
  implementation("androidx.activity:activity-compose:1.12.4")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.navigation:navigation-compose:2.9.7")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
  implementation("androidx.appcompat:appcompat:1.7.1")

  implementation("androidx.credentials:credentials:1.5.0")
  implementation("androidx.credentials:credentials-play-services-auth:1.5.0")

  implementation("io.privy:privy-core:0.9.2")

  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

  implementation("io.coil-kt:coil-compose:2.6.0")

  implementation("androidx.datastore:datastore-preferences:1.1.4")

  testImplementation("junit:junit:4.13.2")
  debugImplementation("androidx.compose.ui:ui-tooling")
}
