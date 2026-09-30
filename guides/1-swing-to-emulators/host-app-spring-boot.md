# The host app — Spring Boot

Reference for [Phase 3](./guide.md#S_copy_seed), if you chose Spring Boot in
[Phase 0](./guide.md#S_choose_bootstrap). Your migrated Swing code needs something to run inside; on
Spring Boot that is a `@SpringBootApplication` starting an embedded Tomcat, with `vaadin-spring`
wiring Vaadin into the context. Spring Boot is the **bootloader** here and nothing more: your code
does not become a bean graph.

**The host app is not listed here — it ships as a real tree you copy**, at
[`seed/spring-boot/`](./seed/spring-boot/), and CI builds it exactly as shipped, wiring test
included. This page says what each of its files is for and which parts of it are not a free choice,
then how to run and package the result. [`build-wiring.md`](./build-wiring.md) carries the
dependencies, the `--add-opens` flag and the JDK-24 floor.

## Three constraints

- **Exactly one `AppShellConfigurator` in the project.** More than one is a Vaadin Flow start-up
  error. If your app grows a second reason to configure the shell — a PWA name, another stylesheet,
  a viewport — those are annotations on the one class you already have.
- **Tomcat's request threads must stay platform threads.** `spring.threads.virtual.enabled` must be
  `false`. That is already Spring Boot's default, so this is not something you switch on; it is
  something a starter or a corporate parent pom can switch *off* underneath you. See
  [§ Virtual threads](#SBS_virtual_threads) below for what happens then.
- **Nothing of yours becomes a bean.** Spring Boot arrives as the servlet bootstrap, not as a
  mandate to rewrite your object graph. Whatever your Swing app did — `new` where needed, a static
  locator — keeps working, and turning it into a bean graph mid-port is scope creep at the worst
  moment. Former singletons go into [`FormerSingletons`](./former-singletons.md) exactly as on
  Vaadin Boot, not into a `vaadin-spring` scope. The reverse case — an app that is *already* built
  on Spring — is **currently unsupported**: an existing singleton bean holding per-user state leaks
  it between users, and no check can find it. See
  [`build-wiring.md`](./build-wiring.md#BW_spring_app).

## What the seed holds

The four classes are in `com.example.app`. Move them into your own package when you copy them —
typically the one holding your main `JFrame`, or its parent — and keep them together there. Under
Spring that package also has to be one `@SpringBootApplication`'s component scan reaches, which
keeping `Main` in it satisfies for free.

| file | what in it is not a free choice |
|---|---|
| `example-pom.xml` → your `pom.xml` | `swingbridge-emulators-spring`, `spring-boot-starter-websocket`, the `spring-boot.version` property, `build-frontend` in the *default* build, the `--add-opens` flag in `<jvmArguments>` and `<argLine>` and as the jar's `Add-Opens` manifest entry, the JDK 24 gate. The comments mark each one, and the sections below explain the ones that bite |
| `Main.java` | `public class`, **not `final`**: `@SpringBootApplication` is a `@Configuration`, which Spring CGLIB-subclasses, so a `final` class fails at context start-up — and `final` is exactly what a Vaadin Boot `Main` carries. `SpringApplication.run` **blocks until the server stops**, so there is no "after boot" inside `main()`; [`lifecycle.md`](./lifecycle.md) is the hook table |
| `AppShell.java` | `@Push` and the Aura stylesheet — [Phase 4](./guide.md#S_app_shell) |
| `AppRoute.java` | `@Route("")`, `@PreserveOnRefresh`, and `bootstrap()` calling your `mainUI()` — [Phase 3](./guide.md#S_add_route) |
| `AppErrorHandler.java` | the body is yours to adapt ([Phase 4](./guide.md#S_error_handler)); it is a `@Component`, which is how it is registered on this lane |
| `resources/application.properties` | `spring.threads.virtual.enabled=false` ([§ Virtual threads](#SBS_virtual_threads)), and the root log level at `info`: that is where the emulators' WARN inventory shows up, which [Phase 6](./guide.md#S_triage_warns) triages. Quiet a chatty dependency by raising *its* level |
| `src/test/…/SpringWiringTest.java` + `src/test/resources/build.properties` | keep both — [§ The wiring test](#SBS_wiring_test) |

**There is no `AppServlet` and no SPI file on this lane**: `swingbridge-emulators-spring`
auto-configures the servlet, its registration and `SwingBridgeEmulatorsBootstrap`, so
[Phase 4](./guide.md#S_app_servlet)'s servlet step and [its SPI step](./guide.md#S_register_bootstrap)
are done for you.

Your app may already log through log4j or `java.util.logging`. Leave it alone — two logging
frameworks coexist fine, and rewriting the call sites is not what makes the app work in a browser
([`platform.md`](./platform.md#looks-like-migration-work--isnt)). To layer your own CSS over stock
Aura, create `src/main/resources/static/styles.css` and add a second `@StyleSheet("styles.css")`
**below** the theme one on `AppShell` — note the directory: Spring Boot serves static resources from
`static/`, where Vaadin Boot uses `webapp/`.

**Gradle is not walked.** The seed and every measurement behind this page are Maven. Spring Boot's
Gradle plugin is a supported way to build a Spring Boot app and nothing here is Maven-specific in
principle, but the exact incantations below — `jvmArguments`, the frontend build, the repackage
behaviour — have Gradle equivalents this guide has not run. If you build with Gradle, expect to work
them out.

## What you do *not* create

Four files belong to the other bootstrap, and an agent working from memory of a Vaadin Flow app will
produce them anyway:

| File | Why not |
|---|---|
| `src/main/resources/webapp/ROOT` | a Vaadin Boot marker; Spring Boot serves static resources from `static/` and needs nothing |
| `src/main/resources/simplelogger.properties` | Logback arrives with the starter and is the binding in effect; leaving slf4j-simple's config *and* its jar is a double binding |
| `META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener` | Vaadin registers every `VaadinServiceInitListener` **bean** on its own, so the SPI file registers a second copy of a listener that is already there |
| `AppServlet.java` | `swingbridge-emulators-spring` registers the servlet for you. Writing one is worse than redundant if you copy the Vaadin Boot shape: a servlet extending `VaadinServlet` (or `SwingBridgeVaadinServlet`) rather than `SpringServlet` loses Vaadin's bean-based route and instantiator discovery |

The first two are inert or redundant; the last two are the ones that bite, because both are
silent. A listener in the SPI file *and* present as a bean runs every per-UI step twice, and a
hand-written servlet on the wrong base class fails at the first route lookup rather than at startup.

## Directory layout, after the copy

```
<project-root>/
├── .gitignore
├── pom.xml                          # from example-pom.xml
├── .mvn/wrapper/maven-wrapper.properties
├── mvnw, mvnw.cmd                   # generate with `mvn wrapper:wrapper`
└── src/
    ├── main/
    │   ├── java/<package>/
    │   │   ├── Main.java            # @SpringBootApplication + your main() / mainUI() split
    │   │   ├── AppShell.java
    │   │   ├── AppRoute.java
    │   │   ├── AppErrorHandler.java
    │   │   └── ... your migrated Swing code
    │   └── resources/
    │       └── application.properties
    └── test/
        ├── java/<package>/SpringWiringTest.java
        └── resources/build.properties
```

## Where the versions come from

Your app resolves SB-Emulators by Maven coordinate, and the seed's `example-pom.xml` pins a matching
set. When you need a version it does not carry, the drift-proof authority is
**`com.vaadin.swingbridge:swingbridge-emulators-parent`'s published pom** — it travels with the artifact you
already resolve, so it cannot disagree with the release you are building against. Read it out of your
local repository
(`~/.m2/repository/com/vaadin/swingbridge/swingbridge-emulators-parent/<version>/swingbridge-emulators-parent-<version>.pom`)
or from Maven Central.

**Spring Boot's own version is the exception, and it needs a word.** The seed pom declares no
`spring-boot-starter-parent` and imports no `spring-boot-dependencies` BOM — deliberately, because a
real enterprise app already has a corporate parent and cannot take Spring's either. Spring Boot's
version therefore arrives from `vaadin-spring-boot-starter`, which declares it on every artifact it
brings; Vaadin 25.3.0 resolves **Spring Boot 4.1.1, Tomcat 11.0.25, Logback 1.5.38**. What does *not*
arrive that way is the `spring-boot-maven-plugin` and the two starters no BOM manages
(`spring-boot-starter-websocket`, `spring-boot-starter-test`), so the pom carries an explicit
`spring-boot.version` property for those.

Nothing in the build keeps that property in step — the seed's `SpringWiringTest` is what does.
Re-derive it after any Vaadin upgrade rather than guessing:

```bash
./mvnw -C dependency:tree -Dincludes=org.springframework.boot
```

Neither half of a mismatch is a build error — a stale plugin repackages a runtime it does not match
and the jar fails at `java -jar`, and stale starters put two Spring Boot versions on one classpath.

**`spring-boot-starter-websocket` is required even though nothing names it.** `@Push` runs over
Atmosphere, whose JSR-356 endpoint Vaadin's Spring auto-configuration deploys through a
`ServerEndpointExporter`. Without the starter the context fails at boot with a `NoClassDefFoundError`
on that class, naming neither Push nor Vaadin. **And no `jakarta.servlet-api` declaration is
needed**, unlike on Vaadin Boot: `tomcat-embed-core`, which carries the servlet API classes itself,
arrives at compile scope.

For anything else — your own app's dependencies, a plugin version — resolve it rather than recalling
it. A **maven-tools MCP is recommended if you have one**, the same way a language server is: it makes
the lookup one call instead of several. Without it, Maven Central's own search endpoint answers over
plain `curl`:

```bash
curl -s 'https://search.maven.org/solrsearch/select?q=g:org.springframework.boot+AND+a:spring-boot&core=gav&rows=5&wt=json'
```

If your app's own build already pinned a version that works, keep it. The migration is not the moment
to upgrade dependencies — see [`platform.md`](./platform.md#looks-like-migration-work--isnt).

## What the auto-configuration does, and how to override it

Declaring `swingbridge-emulators-spring` publishes three things you would otherwise hand-write:

| Bean | Why it is not yours to write |
|---|---|
| `SwingBridgeSpringServlet` | a `SpringServlet` whose session lock is virtual-thread-aware — without that, a virtual thread taking the session lock recurses to `StackOverflowError` with no message, and every blocking dialog fails |
| its `ServletRegistrationBean` | built through Vaadin's own `configureServletRegistrationBean`, which computes the Atmosphere JSR-356 mapping among the init parameters — **hand-rolling this is the trap**, because losing that mapping breaks Push, which breaks every blocking dialog, with no error anywhere |
| `SwingBridgeEmulatorsBootstrap` | `final` and library-owned, so you cannot annotate it; forgetting it is what makes `MainWindowRoute` throw on attach |

**Every one is `@ConditionalOnMissingBean`, so declaring your own wins** — that is the supported way
to customize, not an accident. Note that the bootstrap must *not* also be listed in an SPI file:
Vaadin registers every `VaadinServiceInitListener` bean itself, and the two discovery mechanisms
would run it twice. A listener of *your own*, for non-SB-Emulators reasons, can be a `@Component`
as usual — `AppErrorHandler` is one.

**Subclassing the servlet.** If you need your own `VaadinServletService`, extend
`SwingBridgeSpringServlet` and override `createSwingBridgeService` — `createServletService` is
`final`, because overriding *it* is how the session-lock wrap gets dropped silently. Then publish
your own registration bean, which makes the auto-configuration back off.

<a id="SBS_virtual_threads"></a>

## Virtual threads

`spring.threads.virtual.enabled=false` is Spring Boot's default, so the seed's line changes nothing
the day you copy it. It is there so that a starter, a corporate parent pom or a well-meaning
performance tweak cannot flip it silently.

SB-Emulators parks a virtual thread on top of the request thread for every blocking modal dialog, and
that machinery needs a **platform** carrier underneath. If the property is ever `true`, the app
refuses to start, with an exception naming this property — loudly, rather than hanging. You do not
need to guard for it; you need to not let it drift.

## .gitignore

```
.idea
target
classes
local.properties
*.iml
out
src/main/frontend/generated

!**/src/main/dev-bundle/webapp/VAADIN/build

.project
.classpath
.settings
bin
```

<a id="HSB_run"></a>

## Running it

```bash
./mvnw -C spring-boot:run
```

The plugin forks a JVM for that goal, which is what lets the pom's `<jvmArguments>` pass
`--add-opens java.base/java.lang=ALL-UNNAMED` through. Spring Boot prints its banner and then the
port:

```
Tomcat started on port 8080 (http) with context path '/'
Started Main in 4.2 seconds
```

**Another port:** `./mvnw -C spring-boot:run -Dspring-boot.run.arguments=--server.port=8090`, or
just edit `server.port` in `application.properties`.

**Launching from your IDE's own run configuration skips the pom**, and with it the `--add-opens`
flag. Either set the flag once in the IDE's configuration or always launch through Maven.

<a id="SBS_add_opens"></a>

### The `--add-opens` flag

The virtual-thread executor behind every blocking modal dialog reflects into `java.base/java.lang`.
Without the flag **the app does not start**: it throws while your `invokeLater` builds the main
frame, with a stack trace naming the exact missing opens.

```
InaccessibleObjectException: Unable to make
java.lang.ThreadBuilders$VirtualThreadBuilder(java.util.concurrent.Executor) accessible:
module java.base does not "opens java.lang" to unnamed module
```

That is a won't-boot, not a silent corruption — the failure is as loud as failures get. An app that
builds its UI *without* `invokeLater` gets the same exception later, at its first modal dialog
instead.

The flag is needed in three JVMs, and each gets it differently: the `spring-boot:run` JVM
(`<jvmArguments>` in the pom), the **test** JVM (`<argLine>` on surefire, since your own tests drive
dialogs), and whatever runs the packaged artifact — which is the next section.

One editing trap worth knowing before you touch the pom: **a literal double dash is illegal inside an
XML comment** (XML 1.0 §2.5). Maven rejects the whole pom over it and names neither XML comments nor
your flag. Write the flag dash-less in prose, or keep it out of comments.

## Packaging and launching the artifact

```bash
./mvnw -C clean package
```

**That is the production build — there is no profile to remember.** `vaadin-maven-plugin`'s
`build-frontend` goal sits in the default build, which is what Vaadin's own Spring Boot starter does;
the `-Pproduction` profile you may have seen is the shape Vaadin's docs prescribe for "Jakarta EE or
plain Java, and for better backwards compatibility", and it is what the Vaadin Boot lane uses. Two
things follow, and both are worth knowing before you move the goal:

- **Dev mode is unaffected.** `build-frontend` binds to `prepare-package` and `spring-boot:run` forks
  after `test-compile`, so a dev run never triggers it — measured: `spring-boot:run` logs
  *"A development mode bundle build is not needed"*, `java -jar` logs *"Vaadin is running in
  production mode"*.
- **It is not slow.** With no `@NpmPackage` / `@CssImport` / frontend add-on in the project — which
  is the normal state of a migrated Swing app — `build-frontend` reuses the platform's pre-compiled
  production bundle instead of running npm and Vite. A clean `package` of the shipped example app
  takes about 12 seconds.

If you move `build-frontend` behind a profile anyway, know what you are buying: a plain `package`
then produces a jar that boots and dies with `'vaadin-dev-server' not found`, because `vaadin-dev` is
`<optional>` (correctly — that is how the dev server is kept out of the production package) and a jar
with no production bundle falls back to dev mode. An artifact that looks launchable and is not is a
poor thing to hand a CI step or a `Dockerfile`.

Run the jar once before you call the migration done: Vaadin's dev/prod split hides a class of errors
until the production frontend build runs.

<a id="SBS_launch"></a>

### Getting the flag to the launched JVM

Where the flag can come from depends on **how the artifact is launched**, and the asymmetry is a
property of the `java` launcher rather than of Spring Boot:

| Shape | Launch | `Add-Opens` in the manifest | So the flag comes from |
|---|---|---|---|
| **Fat jar** | `java -jar app.jar` | **honoured** | the manifest, the CLI, or `JDK_JAVA_OPTIONS` |
| **Plain jar** | `java -cp app.jar:lib/* <package>.Main` | **ignored** | the CLI or `JDK_JAVA_OPTIONS` only |
| Extracted (`-Djarmode=tools … extract`) | `java -jar app/app.jar` | honoured | as fat jar |
| WAR in an external Tomcat | `SpringBootServletInitializer` | n/a | `CATALINA_OPTS` — it leaves the app entirely |

**The recommendation is the fat jar**, and it is what you confirmed or changed at
[Phase 0](./guide.md#S_choose_bootstrap). It is Spring Boot's own default, and the only shape that
carries the flag **inside the artifact**, so it cannot be lost by whoever writes the launch line or
the container environment: the seed pom's `maven-jar-plugin` block puts an `Add-Opens` entry in the
manifest, and `spring-boot:repackage` keeps it. The launch line is then plain:

```bash
java -jar target/<app>.jar
```

**If you launch with `-cp` instead** — an enterprise packaging story of your own, with the
`.jar.original` and an external classpath — the manifest entry is ignored and silently does nothing,
so the flag goes on the command line (or in `JDK_JAVA_OPTIONS`):

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED -cp "app.jar:lib/*" <package>.Main
```

The manifest entry stays in the pom either way; it is harmless under `-cp`. Two spelling traps if you
edit it: the manifest form is `java.base/java.lang` with **no `=ALL-UNNAMED`** (it implies the
unnamed module), and it is a *space-separated* list if you ever need more. Whichever shape you
deploy, write its launch line down where whoever runs the app will find it.

<a id="SBS_wiring_test"></a>

## The wiring test

The Spring-specific failure modes above — the servlet registration, a double-registered listener,
the hand-kept `spring-boot.version` — are **silent until the first modal dialog** (or, for the
version, until `java -jar`). The app boots, serves pages and looks healthy. So the seed ships
`SpringWiringTest`, which asserts all three at build time; keep it in your test suite. It reads the
declared version from `src/test/resources/build.properties`, which the pom's `<testResources>` block
filters — keep that block, or the version assertion fails naming it.

Check it is not vacuous before you trust it: list `vaadinx.swing.app.SwingBridgeEmulatorsBootstrap`
in a `META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener` file, or change
`spring-boot.version`, and confirm the matching assertion reddens.

## Further reading

Maintainer-facing, outside this kit — the mechanism rather than the recipe: Vaadin's own
documentation covers Flow and `vaadin-spring`, and Spring Boot's reference documentation covers the
Maven plugin, the packaging shapes and `application.properties`.
