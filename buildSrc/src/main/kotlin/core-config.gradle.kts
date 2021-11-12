//import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
//
///**
// * "Convention plugin" for common plugins, dependencies, etc.
// *
// * Ongoing use of the `subprojects` DSL construct is discouraged.
// *
// * See https://docs.gradle.org/current/samples/sample_building_kotlin_applications_multi_project.html
// */
//plugins {
//    kotlin("jvm")
//    id("org.jetbrains.kotlin.kapt")
//    //id("org.jlleitschuh.gradle.ktlint")
//}
//
//repositories {
//    mavenLocal()
//    mavenCentral()
//}
//
//dependencies {
//    // Align versions of all Kotlin components.
//    // See https://medium.com/@gabrielshanahan/a-deep-dive-into-an-initial-kotlin-build-gradle-kts-8950b81b214
//    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
//    implementation("org.jetbrains.kotlin", "kotlin-reflect")
//    implementation("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
//
//    implementation("ch.qos.logback.contrib", "logback-json-core", Versions.Logback)
//    implementation("ch.qos.logback.contrib", "logback-json-classic", Versions.Logback)
//
//    testImplementation("org.jetbrains.kotlinx", "kotlinx-coroutines-test", Versions.Kotlinx.Core)
//    testImplementation("org.junit.jupiter", "junit-jupiter-api", Versions.JUnit.Core)
//    testImplementation("org.junit.jupiter", "junit-jupiter-engine", Versions.JUnit.Core)
//    testImplementation("org.junit-pioneer", "junit-pioneer", Versions.JUnit.Pioneer)
//}
//
//// Compilation:
//tasks.withType<KotlinCompile> {
//    kotlinOptions {
//        freeCompilerArgs += "-Xjsr305=strict"
//        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
//        jvmTarget = "11"
//    }
//}
//
//// Testing:
//tasks.test {
//    useJUnitPlatform()
//}
