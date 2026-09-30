# The host app — Vaadin Boot

Reference for [Phase 3](./guide.md#S_copy_seed), if you chose Vaadin Boot in
[Phase 0](./guide.md#S_choose_bootstrap). Your migrated Swing code needs something to run inside; on
Vaadin Boot that is a plain `main()` starting an embedded Jetty, with no DI container and no servlet
container to deploy into.

**The host app is not listed here — it ships as a real tree you copy**, at
[`seed/vaadin-boot/`](./seed/vaadin-boot/), and CI builds it exactly as shipped. This page says what
each of its files is for and which parts of it are not a free choice, then how to run and package the
result. [`build-wiring.md`](./build-wiring.md) carries the reasoning behind the build file.

## Two constraints

- **Exactly one `AppShellConfigurator` in the project.** More than one is a Vaadin Flow start-up
  error. If your app grows a second reason to configure the shell — a PWA name, another stylesheet,
  a viewport — those are annotations on the one class you already have.
- **No DI container is introduced by this host.** Your services stay however your Swing app held
  them: `new`ed where needed, or reached through a static locator. That is deliberate — adding a
  container mid-port is scope creep at the worst moment. An app that *already* has Spring keeps it;
  see [`build-wiring.md`](./build-wiring.md#BW_bootstrap).

## What the seed holds

The five classes are in `com.example.app`. Move them into your own package when you copy them —
typically the one holding your main `JFrame`, or its parent — and keep them together there, so the
framework wiring is easy to spot.

| file | what in it is not a free choice |
|---|---|
| `example-pom.xml` → your `pom.xml` (or `example-build.gradle.kts` → `build.gradle.kts`) | the forked-JVM `--add-opens` wiring and its argument order, the JDK 24 gate, on Maven the explicit `compile` scope on `jakarta.servlet-api`, and the version pins. The comments mark each one; [`build-wiring.md`](./build-wiring.md) has why |
| `Main.java` | `useVirtualThreadsIfAvailable(false)` — the blocking-dialog machinery runs its own virtual threads and needs platform request threads under them. `run()` **blocks until the server stops**, so there is no "after boot" inside `main()`; [`lifecycle.md`](./lifecycle.md) is the hook table |
| `AppShell.java` | `@Push` and the Aura stylesheet — [Phase 4](./guide.md#S_app_shell) |
| `AppRoute.java` | `@Route("")`, `@PreserveOnRefresh`, and `bootstrap()` calling your `mainUI()` — [Phase 3](./guide.md#S_add_route) |
| `AppServlet.java` | `urlPatterns = {"/*"}` and `asyncSupported = true`; the base class — [Phase 4](./guide.md#S_app_servlet) |
| `AppErrorHandler.java` | the body is yours to adapt — [Phase 4](./guide.md#S_error_handler) |
| `resources/webapp/ROOT` | nothing — the content is irrelevant, but the file must exist: Vaadin Boot needs it so `webapp/` is found as a classpath resource, and without it the app dies at boot with `IllegalStateException: Invalid state: the resource /webapp/ROOT doesn't exist` from `Env.findWebRoot` |
| `resources/simplelogger.properties` | the root level stays at `info`: that is where the emulators' WARN inventory shows up, which [Phase 6](./guide.md#S_triage_warns) triages. Quiet a chatty dependency by raising *its* threshold |
| `resources/META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener` | both lines — `SwingBridgeEmulatorsBootstrap` ([Phase 4](./guide.md#S_register_bootstrap)) and your `AppErrorHandler`. **It names `com.example.app.AppErrorHandler`, so edit that line when you move the class** — it is a plain-text reference, which a move-refactoring may not rewrite |

Your app may already log through log4j or `java.util.logging`. Leave it alone — two logging
frameworks coexist fine, and rewriting the call sites is not what makes the app work in a browser
([`platform.md`](./platform.md#looks-like-migration-work--isnt)). To layer your own CSS over stock
Aura, create `src/main/resources/webapp/styles.css` and add a second `@StyleSheet("styles.css")`
**below** the theme one on `AppShell`.

## Directory layout, after the copy

```
<project-root>/
├── .gitignore
├── pom.xml                          # from example-pom.xml   (Gradle: build.gradle.kts,
├── .mvn/wrapper/, mvnw, mvnw.cmd    # `mvn wrapper:wrapper`   settings.gradle.kts, gradlew)
└── src/main/
    ├── java/<package>/
    │   ├── Main.java                # your main() / mainUI() split lives here
    │   ├── AppShell.java
    │   ├── AppRoute.java
    │   ├── AppServlet.java
    │   ├── AppErrorHandler.java
    │   └── ... your migrated Swing code
    ├── resources/
    │   ├── simplelogger.properties
    │   ├── META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener
    │   └── webapp/ROOT
    ├── assembly/zip.xml             # only if you want a runnable distribution (below)
    └── scripts/app                  # its launcher
```

## Where the versions come from

Your app resolves SB-Emulators by Maven coordinate, and the two seed build files pin a matching set
(SB-Emulators, Vaadin, slf4j, vaadin-boot). When you need a version they don't carry, the drift-proof
authority is **`com.vaadin.swingbridge:swingbridge-emulators-parent`'s published pom** — it travels with the
artifact you already resolve, so it cannot disagree with the release you are building against. Read
it out of your local repository
(`~/.m2/repository/com/vaadin/swingbridge/swingbridge-emulators-parent/<version>/swingbridge-emulators-parent-<version>.pom`)
or from Maven Central.

For anything else — your own app's dependencies, a plugin version — resolve it rather than recalling
it. A **maven-tools MCP is recommended if you have one**, the same way a language server is: it makes
the lookup one call instead of several. Without it, Maven Central's own search endpoint answers over
plain `curl`, which is enough:

```bash
curl -s 'https://search.maven.org/solrsearch/select?q=g:org.slf4j+AND+a:slf4j-simple&core=gav&rows=5&wt=json'
```

If your app's own build already pinned a version that works, keep it. The migration is not the moment
to upgrade dependencies — see [`platform.md`](./platform.md#looks-like-migration-work--isnt).

## .gitignore

```
.idea
build
.gradle
classes
local.properties
*.iml
out
target
src/main/frontend/generated

!**/src/main/dev-bundle/webapp/VAADIN/build

.project
.classpath
.settings
bin
```

<a id="HVB_run"></a>

## Running it

```bash
# Maven — forked JVM, so the --add-opens flag applies. NOT exec:java, which runs
# inside Maven's own JVM and cannot pass a startup flag.
./mvnw -C compile exec:exec

# Gradle — the `run` task applies applicationDefaultJvmArgs for you.
./gradlew run
```

Vaadin Boot prints its banner once it is up:

```
opening browser to http://localhost:8080
Press ENTER or CTRL+C to shutdown
```

**Another port:** `SERVER_PORT=8090 ./mvnw -C compile exec:exec`. Not `-Dserver.port` — with
`exec:exec` that reaches Maven's own JVM rather than the forked one, so the app binds 8080 and
nothing tells you the flag was dropped ([`build-wiring.md`](./build-wiring.md#BW_port)).

**Launching from your IDE's own run configuration skips the build file**, and with it the
`--add-opens` flag, so the app refuses to start. Either set the flag once in the IDE's configuration
or always launch through the build tool.

### Static resource hot-reload

Vaadin Boot serves `src/main/resources/webapp/` off the classpath, and in dev mode the classpath
points at build output rather than at `src/`:

| build tool | dev-mode classpath |
|---|---|
| Gradle | `build/resources/main/webapp/` |
| Maven | `target/classes/webapp/` |

So an edit under `src/` goes live only after the resource-copy task runs — `./gradlew processResources`
or `./mvnw process-resources`. The next request then serves the new bytes, with no restart. Handy
check: every file in `webapp/` is served at its URL, the marker included, so
`curl http://localhost:8080/ROOT` returns its contents.

### Java class hot-reload

There isn't one worth relying on. `./gradlew -t run` with a concurrent `./gradlew classes` does not
apply the change to the running app; restart it, or run under a HotswapAgent-enabled JBR.

## Packaging a distribution (Maven, optional)

Only needed if you want a runnable zip rather than a dev-mode run — step 1 ends at a dev-mode
`exec:exec`, so nothing above requires this and the seed's `example-pom.xml` deliberately leaves it
out.

Two files, plus `maven-assembly-plugin` wired to the `zip.xml` descriptor. **Create both files before
you add the plugin:** bound to `package` against a descriptor that does not exist yet, it fails the
build on a missing descriptor rather than on anything you would recognise.

`src/main/assembly/zip.xml` bundles the app's jars under `lib/` and the launcher under `bin/`,
excluding `vaadin-dev` so a production run doesn't ship the dev bundle:

```xml
<assembly xmlns="http://maven.apache.org/ASSEMBLY/2.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/ASSEMBLY/2.0.0 http://maven.apache.org/xsd/assembly-2.0.0.xsd">
    <id>zip</id>
    <formats>
        <format>zip</format>
        <format>tar.gz</format>
    </formats>
    <includeBaseDirectory>false</includeBaseDirectory>
    <fileSets>
        <fileSet>
            <directory>${project.basedir}/src/main/scripts</directory>
            <outputDirectory>bin</outputDirectory>
            <fileMode>0755</fileMode>
        </fileSet>
    </fileSets>
    <dependencySets>
        <dependencySet>
            <outputDirectory>lib</outputDirectory>
            <unpack>false</unpack>
            <useTransitiveFiltering>true</useTransitiveFiltering>
            <excludes>
                <exclude>com.vaadin:vaadin-dev</exclude>
            </excludes>
        </dependencySet>
    </dependencySets>
</assembly>
```

`src/main/scripts/app` is the launcher it ships in `bin/`. **Add the `--add-opens` flag here too** —
this is the JVM your users actually run:

```bash
#!/bin/bash
set -e -o pipefail
BASE_DIR=$(dirname "$0")/..
cd $BASE_DIR/lib
CLASSPATH=$(ls|tr '\n' ':')
java --add-opens java.base/java.lang=ALL-UNNAMED -cp "$CLASSPATH" <package>.Main "$@"
```

Then `./mvnw -C clean package -Pproduction` produces the distribution, and
`./gradlew clean build -Pvaadin.productionMode` does the Gradle equivalent at
`build/distributions/app.zip`. Run a production build once before you call the migration done:
Vaadin's dev/prod split hides a class of errors until the production frontend build runs.

## Further reading

Maintainer-facing, outside this kit — the mechanism rather than the recipe:
[vaadin-boot's own README](https://github.com/mvysny/vaadin-boot) is the authority on the library
itself (service locator, Jetty config, PWA, push, SSL, clustering, Docker), and the Vaadin
documentation covers Flow.
