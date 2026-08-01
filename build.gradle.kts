plugins {
    // AGP 9 ships Kotlin support built in; applying kotlin-android alongside it is an
    // error now. See DECISIONS.md.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
