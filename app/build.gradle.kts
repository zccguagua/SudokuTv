plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 题库是固定数据，已随仓库提交在 app/src/main/assets/bank/，构建不依赖任何外部仓库。
val puzzleBankAssetDir = layout.projectDirectory.dir("src/main/assets/bank")
val puzzleBankNames = listOf("easy", "medium", "hard", "diabolical")
val puzzleBankRecordBytes = 42L
val puzzleBankRetainedCount = 20_000L

val verifyPuzzleBank by tasks.registering {
    group = "puzzle bank"
    description = "检查内置题库资产是否齐全"
    val dir = puzzleBankAssetDir
    doLast {
        puzzleBankNames.forEach { name ->
            val file = File(dir.asFile, "$name.bin")
            check(file.isFile) { "缺少题库资产：${file.path}（应从版本库恢复）" }
            check(file.length() == puzzleBankRetainedCount * puzzleBankRecordBytes) {
                "题库资产题数异常：${file.path}（${file.length()} 字节，应为 " +
                    "${puzzleBankRetainedCount * puzzleBankRecordBytes} 字节 / $puzzleBankRetainedCount 题）"
            }
        }
    }
}

android {
    namespace = "com.zcc.sudokutv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zcc.sudokutv"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress.add("bin") }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    testOptions.unitTests.isIncludeAndroidResources = true
}

tasks.named("preBuild") { dependsOn(verifyPuzzleBank) }

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
}
