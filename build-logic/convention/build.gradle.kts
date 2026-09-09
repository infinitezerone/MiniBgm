plugins {
    `kotlin-dsl`
}

group = "com.infinitezerone.minibgm.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "minibgm.kmp.library"
            implementationClass = "com.infinitezerone.minibgm.KmpLibraryConventionPlugin"
        }
        register("kmpRoom") {
            id = "minibgm.kmp.room"
            implementationClass = "com.infinitezerone.minibgm.KmpRoomConventionPlugin"
        }
        register("androidApplication") {
            id = "minibgm.android.application"
            implementationClass = "com.infinitezerone.minibgm.AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "minibgm.android.application.compose"
            implementationClass = "com.infinitezerone.minibgm.AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "minibgm.android.library"
            implementationClass = "com.infinitezerone.minibgm.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "minibgm.android.library.compose"
            implementationClass = "com.infinitezerone.minibgm.AndroidLibraryComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "minibgm.android.feature"
            implementationClass = "com.infinitezerone.minibgm.AndroidFeatureConventionPlugin"
        }
        register("crapCheck") {
            id = "minibgm.crap.check"
            implementationClass = "com.infinitezerone.minibgm.crap.CrapCheckPlugin"
        }
    }
}
