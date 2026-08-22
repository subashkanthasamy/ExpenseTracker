plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    @Suppress("DEPRECATION")
    androidLibrary {
        namespace = "com.bose.expensetracker.shared"
        compileSdk = 36
        minSdk = 24
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    // Web target. `binaries.library()` emits an npm-consumable module and
    // `generateTypeScriptDefinitions()` gives the React side real types.
    js(IR) {
        // Without this the npm package name inherits `rootProject.name` ("Expense Tracker")
        // and yarn rejects it: "package.json: Name contains illegal characters".
        outputModuleName.set("expensetracker-shared")
        // `browser` is what the library output targets. The tests run on Node instead:
        // shared/ touches no DOM, and a browser test task would need a headless Chrome
        // installed just to run `:shared:allTests`.
        browser {
            testTask { enabled = false }
        }
        nodejs()
        // ES modules rather than the default UMD: Vite can tree-shake them, and the React
        // side gets plain `import { ... }` instead of a global-object lookup.
        useEsModules()
        binaries.library()
        generateTypeScriptDefinitions()

        // Kotlin/JS reports an unexportable type in an `@JsExport` signature (`Long`, a
        // commonMain data class, `List`) as a WARNING and still emits the module, with the
        // offending type degraded in the .d.ts. That is a silent break of the TypeScript
        // contract, so warnings are errors here.
        compilerOptions {
            allWarningsAsErrors.set(true)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Koin DI
            implementation(libs.koin.core)

            // Serialization
            implementation(libs.kotlinx.serialization.json)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // DateTime
            implementation(libs.kotlinx.datetime)

            // Multiplatform Settings
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
        }

        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.kotlinx.coroutines.android)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
