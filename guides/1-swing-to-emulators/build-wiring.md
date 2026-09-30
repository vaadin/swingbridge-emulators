# Build wiring — bootstrap, dependencies, plugins, flags

Reference for [Phase 0](./guide.md#S_choose_bootstrap), [Phase 3](./guide.md#S_wire_build) and
[Phase 4](./guide.md#S_add_opens). Everything here is one-time build-file work: which bootstrap
hosts the migrated app, what it depends on, and the four plugin corners that fail in ways the error
message does not name.

**You do not have to assemble any of it.** Each bootstrap's seed carries a complete build file —
[`seed/vaadin-boot/example-pom.xml`](./seed/vaadin-boot/example-pom.xml) (or its
[`example-build.gradle.kts`](./seed/vaadin-boot/example-build.gradle.kts)) and
[`seed/spring-boot/example-pom.xml`](./seed/spring-boot/example-pom.xml) — which CI builds as
shipped. This page is the *why*, for when you need to change something or diagnose a failure; the
two host-app docs, [`host-app-vaadin-boot.md`](./host-app-vaadin-boot.md) and
[`host-app-spring-boot.md`](./host-app-spring-boot.md), cover the rest of each seed.

**Every seed build file pins Java 24 for compilation as well as runtime.** The floor is a runtime one
(see [the JDK floor](#BW_jdk_floor)), and since the JVM has to be 24+ anyway, targeting 24 keeps one
number in the build file instead of two. Emitting older bytecode is still supported — lower
`maven.compiler.release` / `targetCompatibility` and leave the enforcer rule alone. **One kind of app
needs the two numbers:** one that keeps a Spring 5 container, which cannot scan Java 24 class files
([below](#BW_spring5_bytecode)).

<a id="BW_bootstrap"></a>

## Which bootstrap?

The bootstrap is the library that starts the servlet container and hands Vaadin its request
threads: **Spring Boot** (an embedded Tomcat, started by `SpringApplication.run`) or **Vaadin Boot**
(a plain `main()` starting an embedded Jetty, no DI container). Both are fully supported and the
migration is the same on both — every phase of the guide applies unchanged, and what differs is only
which seed you copy in Phase 3 and which host-app doc you read. The `vaadinx` libraries are ordinary
Vaadin Flow add-ons and cannot tell the two apart. Switching later touches none of your migrated
code, only the host-app files.

**We recommend Spring Boot, because Vaadin does.** It is what start.vaadin.com hands you, what the
Vaadin documentation assumes, and what every "how do I do X in Vaadin" answer you will search for
during and after the port is written against. Vaadin Boot is the lighter host — a plain `main()`, no
container, no Spring version to line up — and a first-class choice rather than a fallback.

Pick by what your Swing app already is:

| your app | bootstrap |
|---|---|
| **no DI container** (most Swing apps) | **Spring Boot**, recommended. Nothing of yours becomes a bean — Spring Boot is the bootloader and nothing more ([below](#BW_no_beans)) |
| **already built on Spring**, on Spring 7 | either. **Unsupported on both** — [below](#BW_spring_app) |
| **already built on Spring**, on Spring 6 | Vaadin Boot, with your Spring kept as a plain library. Spring Boot would first need the upgrade to Spring 7. **Unsupported on both** |
| **already built on Spring**, on Spring 5 or older | Vaadin Boot at `maven.compiler.release` 21 ([below](#BW_spring5_bytecode)). Spring Boot would first need the upgrade to Spring 7. **Unsupported on both** |

**Why the Spring rows lean the other way.** Vaadin 25 requires Spring Boot 4 / Spring Framework 7
(and Jakarta EE 11), so a desktop app on Spring 6 or older has to upgrade Spring *before* the import
swap can even be tried on Spring Boot — two migrations debugged at once, where a failure in one is
hard to tell from a failure in the other. On Vaadin Boot, `main()` builds your context exactly as
today and the service locator delegates to `context.getBean(…)`, leaving your Spring version alone.
So the apps most likely to be better off on Vaadin Boot are precisely the ones already on Spring. Take
Spring Boot there only if your Spring is on 7 already, or you had decided to upgrade it anyway.

**An agent asks rather than chooses**: it offers this recommendation and has the user confirm or
change it, and on Spring Boot also confirms the launch shape — a fat jar with `java -jar`,
recommended, or `java -cp` ([`host-app-spring-boot.md`](./host-app-spring-boot.md#SBS_launch)).
**Without a human to ask**, it takes Spring Boot and the fat jar for an app with no DI container, and
Vaadin Boot for one already built on Spring, since that is the road that changes nothing about their
Spring — and says which it took.

<a id="BW_no_beans"></a>
**On Spring Boot your former singletons do not become beans.** Spring Boot is the bootloader here and nothing more: what the [`static` sweep](./static-fields.md) routes to tab scope goes into [`FormerSingletons`](./former-singletons.md) exactly as it does on Vaadin Boot, so the migrated code is the same whichever host you pick, and switching hosts later touches none of it. Don't reach for `vaadin-spring`'s scopes instead — it has no scope of the right shape. `@UIScope` is the F5 trap [`static-fields.md`](./static-fields.md#the-three-scopes) warns about for UI-scoped holders (a browser refresh destroys the `UI` and the bean with it); `@VaadinSessionScope` is one user rather than one running app, and ends nothing when the tab closes; and neither is a tab scope.

<a id="BW_spring_app"></a>**Why an app already built on Spring is unsupported: bean scope.** A desktop `ApplicationContext` serves one user; a web one serves every user at once. A singleton bean holding per-user state (current user, open document, selection) is exactly the "former singleton" the `static` sweep hunts, in bean form — except that bean state lives in instance fields, so neither the sweep nor the Phase 6 guardrails can find it, and an app can pass every check while one user's data shows up in another user's session. There is no mechanical remedy yet. If you migrate one anyway, audit every singleton bean by hand, and drive two browser sessions side by side ([Phase 6](./guide.md#S_multi_tab)) far harder than you otherwise would.

<a id="BW_spring5_bytecode"></a>
### Watch out: keeping Spring 5 on Vaadin Boot? Pin `maven.compiler.release` to 21

Host (a) above runs on the JDK 24+ runtime — but Spring 5.3 can only read class files up to Java 21.
Its component scan parses **your own class files** with a bundled ASM that predates Java 22
bytecode, so at the seed build files' `release` 24 the context refuses to start with
`IllegalArgumentException: Unsupported class file major version 68`. The message names neither
Spring's scanner nor your build setting. The fix is `<maven.compiler.release>21</maven.compiler.release>`
(Gradle: `targetCompatibility = JavaVersion.VERSION_21`), with the JDK 24 runtime and its enforcer
rule left as they are. Spring's runtime proxy generation is unaffected and needs no flag of its own.
Spring 6.2 scans Java 25 bytecode, so a Spring 6 app needs none of this. No upstream fix is coming:
5.3.39 is the last 5.3.x release.

## The dependencies

**Both bootstraps** need the `:emulators` artifact (`com.vaadin.swingbridge:swingbridge-emulators`),
`swingbridge-migration-annotations` at compile scope, and `com.vaadin:vaadin-dev` (dev-mode only).
Then per bootstrap:

| | what else | what not |
|---|---|---|
| **Spring Boot** | `com.vaadin:vaadin-spring-boot-starter`, `swingbridge-emulators-spring` (the host-app wiring, auto-configured), `spring-boot-starter-websocket` | `vaadin-core` (the starter brings it), `jakarta.servlet-api` (Tomcat carries it) |
| **Vaadin Boot** | `com.vaadin:vaadin-core`, `com.github.mvysny.vaadin-boot:vaadin-boot`, `org.slf4j:slf4j-simple` — **plus, on Maven, `jakarta.servlet:jakarta.servlet-api`** ([below](#BW_servlet_scope)) | `swingbridge-emulators-spring` |

The seed build files carry a matching version set; each host-app doc says where to get a version
they do not, and [`host-app-spring-boot.md`](./host-app-spring-boot.md) why the websocket starter is
there when nothing names it.

`:surrogates` and the blocking-dialog machinery are **transitive** — you declare neither. Sanity check
while you are in the build file: `mvn dependency:tree` (or `./gradlew :myapp:dependencies`) should list
`com.vaadin.swingbridge:swingbridge-surrogates` and
`com.github.mvysny.vaadin-blocking-dialogs:vaadin-blocking-dialogs` + `vaadin-uifiber-loom` under your
`:emulators` dependency — **coordinates, not package names, so grep for `swingbridge` and
`blocking-dialogs`**. One more transitive row, `com.github.mvysny.vaadintabscope:tab-scope`, arrives
with them and is expected. All of them are transitive, so you declare none; if they are missing, your
`:emulators` declaration isn't taking and the build will fail at compile.

<a id="BW_servlet_scope"></a>
### Watch out (Vaadin Boot, Maven): declare `jakarta.servlet:jakarta.servlet-api` yourself, at an explicit `<scope>compile</scope>`

Your app **embeds** its servlet container instead of being deployed into one, so there is nobody to
"provide" the API jar. It does arrive transitively from Jetty — at `compile` scope, and everything
would be fine — except that **importing `com.vaadin:vaadin-bom` manages it to `provided`**, which is
correct for a WAR and wrong here, and which drops it off the *runtime* classpath that `exec:exec` and
`exec:java` both use. The explicit scope is required rather than decoration: a declaration that omits
`<scope>` inherits the managed `provided` and changes nothing.

Get it wrong and the app compiles clean, Vaadin Boot starts, and then dies with
`NoClassDefFoundError: jakarta/servlet/ServletContext` (or `ServletRequest`, depending on which class
the container reaches first) — a message that names neither a dependency nor a scope.
`dependency:build-classpath` is no help either: it lists the jar under both
`-Dmdep.includeScope=compile` and `=runtime`. The one command that shows it is `dependency:list`,
whose output carries the scope:

```xml
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.1.0</version>
    <scope>compile</scope>
</dependency>
```

**Gradle is unaffected as long as you don't import the BOM as a platform.** Without
`platform("com.vaadin:vaadin-bom:…")`, Jetty's transitive `jakarta.servlet-api` stays on
`runtimeClasspath` and there is nothing to declare. If you do add the platform, the same `provided`
mapping bites and the same explicit declaration fixes it.

<a id="BW_add_opens"></a>
## The `--add-opens` flag

```
--add-opens java.base/java.lang=ALL-UNNAMED
```

Required by the virtual-thread executor that backs blocking modal dialogs: without it the app
refuses to start, with an `InaccessibleObjectException` naming `java.lang`. It goes in your **dev run
config, prod run config, and test JVM config**, and every seed build file already carries it. On
Spring Boot it is `<jvmArguments>` on `spring-boot-maven-plugin` plus surefire's `<argLine>` —
[`host-app-spring-boot.md`](./host-app-spring-boot.md#SBS_add_opens) covers those and the packaged
jar. The Vaadin Boot wiring:

```kotlin
// Gradle (build.gradle.kts) — inside the `application { ... }` block:
application {
    applicationDefaultJvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}
```

```xml
<!-- Maven — exec-maven-plugin. Run with `./mvnw -C compile exec:exec`, NOT exec:java:
     this is a JVM *startup* flag, and exec:java runs inside Maven's own JVM. -->
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.6.3</version>
    <configuration>
        <executable>${java.home}/bin/java</executable>
        <arguments>
            <argument>--add-opens</argument>
            <argument>java.base/java.lang=ALL-UNNAMED</argument>
            <argument>-classpath</argument>
            <classpath/>
            <argument>com.myapp.Main</argument>   <!-- your main()-hosting class -->
        </arguments>
    </configuration>
</plugin>
```

**The argument order is load-bearing, and getting it wrong fails silently.** JVM flags and
`-classpath` come **before** the main class; anything after the main class is handed to your *app* as
a program argument, so a misplaced `--add-opens` is ignored without a word, and the app then refuses
to start with the `InaccessibleObjectException` this flag exists to prevent. Worth a comment in your pom so the next person
doesn't tidy the list — and write the flag as `add-opens` in that comment, without the leading
dashes: a literal `--` is illegal inside an XML comment (XML 1.0 §2.5), and Maven rejects the whole
POM with `in comment after two dashes (--) next character must be > not a`, which names neither XML
comments nor the flag.

Your test JVM needs the same flag — anything that drives a modal dialog runs its continuation on a
virtual thread too, the guardrail gate included:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.6</version>
    <configuration>
        <argLine>--add-opens java.base/java.lang=ALL-UNNAMED</argLine>
    </configuration>
</plugin>
```

Your IDE's personal run config needs it too — set it once per app in IDE preferences, or always
launch via the Gradle/Maven `run` task so the build-file wiring picks it up.

<a id="BW_jdk_floor"></a>
## The JDK 24 floor, and the build gate for it

**The same executor puts a floor under the JDK itself: your app must *run* on 24 or newer.** Before
[JEP 491](https://openjdk.org/jeps/491), a virtual thread that parks inside a `synchronized` region
pins its carrier — and the carrier here is the Vaadin request thread holding the session lock. So the
response never flushes, the dialog never renders, and the click that would resume the parked thread
can never arrive: a deadlock, every time, independent of machine size. The trigger is *any* monitor
above a modal call — your own `synchronized void save()`, an `Object.wait`, a lock inside a
third-party library or inside the JDK — so there is nothing you can grep for and nothing to refactor
around. Only the JDK version fixes it.

**The seed build files target 24 as well as requiring it**, because the JVM has to be 24+ regardless and one
number beats two. Your *bytecode* may still drop to 21 if another consumer needs it — lower
`maven.compiler.release` / `targetCompatibility` and leave this rule in place; it is the JVM you
launch on that has to be 24+, not what you compile to. SB-Emulators refuses an older runtime at servlet init with a
message naming the version, so a wrong JDK fails loudly at first page load rather than hanging — and a
build gate moves that failure earlier still:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <version>3.6.3</version>
    <executions>
        <execution>
            <id>enforce-jdk-24-plus</id>
            <goals><goal>enforce</goal></goals>
            <configuration>
                <rules>
                    <requireJavaVersion>
                        <version>[24,)</version>
                        <message>A migrated app needs a JDK 24+ runtime (JEP 491 unpins the modal-dialog executor).</message>
                    </requireJavaVersion>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

On Gradle, the one-liner is a `check`-time assertion instead:
`require(JavaVersion.current() >= JavaVersion.VERSION_24) { "…" }`.

<a id="BW_port"></a>
## Running it on Vaadin Boot, and the port trap

On Spring Boot it is `./mvnw -C spring-boot:run`, and the port is `server.port` —
[`host-app-spring-boot.md`](./host-app-spring-boot.md#HSB_run). On Vaadin Boot:

```
# Maven — forked JVM so the --add-opens flag applies
./mvnw compile exec:exec

# Gradle
./gradlew build
./gradlew run
```

***Watch out: to run on another port, use the `SERVER_PORT` environment variable*** —
`SERVER_PORT=8090 ./mvnw -C compile exec:exec`. Vaadin Boot honours `-Dserver.port` too, and with
`exec:exec` that is the trap: a `-D` on the `mvn` command line reaches Maven's own JVM, not the
forked one whose arguments the pom fixes, so the app still binds 8080 and nothing tells you the flag
was ignored. The environment variable is inherited by the fork and needs no pom edit. Worth having
before you need it: running the migrated app beside the original takes two ports.

<a id="BW_test_deps"></a>
## The test dependencies, when Phase 6 adds the first test

[Phase 6](./guide.md#S_guardrails) is where the migration's first test arrives, so it is also where
JUnit and a runner do. Four entries, and the last two are the ones that cost a round-trip if they are
missing:

```
org.junit.jupiter:junit-jupiter                <scope>test</scope>   — the @Test API and engine
org.junit.platform:junit-platform-launcher     <scope>test</scope>   — NO version of its own; the BOM supplies it
org.apache.maven.plugins:maven-surefire-plugin                       — without it nothing runs the test
org.junit:junit-bom                            <scope>import</scope> — in <dependencyManagement>
```

***Watch out: import `junit-bom` BEFORE `vaadin-bom`.*** Among competing `<scope>import</scope>` BOMs
the **first declaration wins**, and `vaadin-bom` manages `junit-jupiter-api` at an older 6.0.x.
Imported second, `junit-bom` loses exactly that one artifact — which is enough for surefire to abort
the forked JVM with `conflicting versions detected: org.junit.jupiter.api 6.0.3,
org.junit.jupiter.engine 6.1.3`.

The guardrails themselves are two more coordinates, and their scopes differ on purpose:

```
com.vaadin.swingbridge:swingbridge-migration-guardrails:${swingbridge-emulators.version}   <scope>test</scope>
com.vaadin.swingbridge:swingbridge-migration-annotations:${swingbridge-emulators.version}
```

SB-Emulators publishes **no BOM**, so both versions are explicit. `migration-annotations` takes the
**default (compile) scope** — the annotation is carried by your `src/main` sources, not by your tests,
so inheriting `migration-guardrails`' `test` scope alongside it makes the annotation invisible to
every field that needs it. `migration-guardrails` is test-scope, and neither it nor the ArchUnit it
runs on reaches your runtime.

## See also

- [`host-app-spring-boot.md`](./host-app-spring-boot.md) / [`host-app-vaadin-boot.md`](./host-app-vaadin-boot.md) — the rest of each seed, and where versions come from.
- [Phase 4](./guide.md#S_app_servlet) — the servlet and session wiring these flags serve.
