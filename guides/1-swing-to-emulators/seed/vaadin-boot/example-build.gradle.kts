// =====================================================================================
// A COMPLETE build.gradle.kts FOR A MIGRATED SWING APP ON VAADIN BOOT — the Gradle twin of
// example-pom.xml beside it. Copy it and edit freely:
//
//     cp swingbridge-migration/seed/vaadin-boot/example-build.gradle.kts build.gradle.kts
//
// Then change three things and add your own dependencies:
//
//   1. group / version                     — your app's coordinates
//   2. mainClass                           — your main()-hosting class, once you have moved
//                                            the seed's classes into your own package
//   3. swingbridgeEmulatorsVersion         — the version you are migrating against
//                                            (the kit's README states it)
//
// Everything else works as it stands. Run it with:
//
//     ./gradlew run
//
// Run it that way rather than launching the class from your IDE's default configuration:
// the JVM flag below is what keeps blocking modal dialogs from deadlocking, and only the
// `run` task applies it for you.
//
// This is a starting point, not a frozen template. The comments mark the few places where
// a plausible-looking edit breaks the app at runtime rather than at build time.
// =====================================================================================

plugins {
    application
    id("com.vaadin") version "25.3.0"
}

group = "com.myapp"
version = "1.0-SNAPSHOT"

val swingbridgeEmulatorsVersion = "0.1-SNAPSHOT"
val vaadinVersion = "25.3.0"
val slf4jVersion = "2.0.18"
val junitVersion = "6.1.3"

repositories {
    mavenCentral()
    // Only needed on a kit built from source, where the coordinate resolves from the local
    // ~/.m2 that `./mvnw -C clean install` populated. Harmless otherwise.
    mavenLocal()
    maven { setUrl("https://maven.vaadin.com/vaadin-addons") }
}

// Java 24 is the floor, and it is a RUNTIME requirement first: before JEP 491 a virtual thread
// that parks inside a synchronized region pins its carrier, and the carrier here is the Vaadin
// request thread holding the session lock — so every blocking modal dialog deadlocks on 23 and
// below. Since the JVM must be 24+ anyway, compiling to 24 costs nothing and keeps one number
// in the file instead of two. (If you must emit older bytecode for another consumer, lower
// these two and leave the check below alone — it is the one that matters.)
java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}

// Gradle's equivalent of the Maven enforcer rule: moves the JDK-24 failure from first page load
// to build time. Without it, a build on 24 deployed onto 21 passes every gate and then hangs on
// its first dialog.
tasks.named("check") {
    doFirst {
        require(JavaVersion.current() >= JavaVersion.VERSION_24) {
            "A migrated app needs a JDK 24+ runtime (JEP 491 unpins the modal-dialog executor); " +
                "this build is running on ${JavaVersion.current()}."
        }
    }
}

dependencies {
    // The import-swap target. :surrogates and the blocking-dialog machinery arrive transitively — declare neither.
    implementation("com.vaadin.swingbridge:swingbridge-emulators:$swingbridgeEmulatorsVersion")

    // Not testImplementation: @IntentionallyStatic is carried by your src/main sources, so a
    // test-only declaration would hide it from every field that needs it.
    implementation("com.vaadin.swingbridge:swingbridge-migration-annotations:$swingbridgeEmulatorsVersion")

    // ADD-ONS: one per third-party Swing library you route through addons.md. Uncomment what you
    // need and DELETE the upstream coordinate it replaces (com.toedter:jcalendar,
    // com.jgoodies:forms) — leaving both means the upstream classes are still on the classpath
    // and your imports may bind back to them.
    // implementation("com.vaadin.swingbridge:swingbridge-emulators-jcalendar-1.4:$swingbridgeEmulatorsVersion")
    // implementation("com.vaadin.swingbridge:swingbridge-emulators-jgoodies-forms-1.2.1:$swingbridgeEmulatorsVersion")

    // PRINTING, optional: only if your app hands a Printable to a PrinterJob. Pulls in PDFBox.
    // implementation("com.vaadin.swingbridge:swingbridge-emulators-printing:$swingbridgeEmulatorsVersion")

    implementation("com.vaadin:vaadin-core:$vaadinVersion") {
        exclude(group = "javax.annotation", module = "javax.annotation-api")
    }
    implementation("com.vaadin:vaadin-dev:$vaadinVersion")
    implementation("com.github.mvysny.vaadin-boot:vaadin-boot:13.7")
    implementation("org.slf4j:slf4j-simple:$slf4jVersion")

    // NOTE ON jakarta.servlet-api: unlike the Maven build, you declare nothing here. Jetty brings
    // it transitively at the right scope. It only becomes a problem if you import vaadin-bom as a
    // platform(...) — that maps it to `provided`, drops it off runtimeClasspath, and the app dies
    // at boot with NoClassDefFoundError: jakarta/servlet/ServletContext. If you add the platform,
    // add an explicit `implementation("jakarta.servlet:jakarta.servlet-api:6.1.0")` with it.

    // ADD YOUR APP'S OWN DEPENDENCIES HERE — whatever its pre-migration build declared, minus the
    // Look-and-Feel libraries (delete those outright) and minus any Swing library you replaced
    // with an add-on above.

    // The migration guardrails: a build-time gate, test-only so neither it nor the ArchUnit it
    // runs on reaches your runtime. It checks that every static is provably immutable or
    // annotated, that no component sits in a static field, and that nothing exits the JVM — which
    // is what keeps the static sweep done once you have done it.
    testImplementation("com.vaadin.swingbridge:swingbridge-migration-guardrails:$swingbridgeEmulatorsVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    // The one line you must change: your main()-hosting class.
    mainClass = "com.example.app.Main"

    // Required by the virtual-thread executor behind blocking modal dialogs. Without it the app
    // refuses to start, with an InaccessibleObjectException naming java.lang.
    applicationDefaultJvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // The guardrail gate runs here, and anything of yours that drives a modal dialog runs its
    // continuation on a virtual thread, so the test JVM needs the same flag as the app JVM.
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
