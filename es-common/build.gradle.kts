plugins {
    kotlin("jvm")
    kotlin("kapt")

    id("core-config")
    id("with-test-fixtures")
    jacoco
}

dependencies {
    implementation(libs.fasterxml.jackson.module.kotlin)
}

kapt {
    correctErrorTypes = true
}
