# `:emulators-spring`

Optional add-on module for [`:emulators`](../emulators): the host-app wiring a migrated app needs
**on Spring Boot**, so that declaring this one coordinate is the whole of the Spring lane's wiring
— no servlet, no `ServletRegistrationBean` and no bootstrap bean in the migrated app's own source.

## Depend

```xml
<dependency>
    <groupId>com.vaadin.swingbridge</groupId>
    <artifactId>swingbridge-emulators-spring</artifactId>
    <version>&lt;version&gt;</version>
</dependency>
```

It pulls `:emulators` and `vaadin-spring`. Nothing in SB-Emulators depends back on it, and it is
the only module in the reactor that may take a Spring dependency.

## What it does

- **`SwingBridgeSpringServlet`** — the `SpringServlet` whose session lock is virtual-thread-aware,
  the Spring twin of `:emulators`' `SwingBridgeVaadinServlet`. A modal Swing dialog parks a virtual
  thread on the request thread, and without the wrapped lock that park deadlocks the session
  ([D_vt_aware_session_lock](../emulators/decisions.md#D_vt_aware_session_lock)).
- **`SwingBridgeAutoConfiguration`** — registers that servlet in place of the one Vaadin's own
  auto-configuration would publish, publishes `SwingBridgeEmulatorsBootstrap` as a bean, and refuses
  at context refresh a deployment that serves Vaadin requests on virtual threads
  (`spring.threads.virtual.enabled=true`). It is discovered through Spring Boot's
  `AutoConfiguration.imports`, never `META-INF/services`, and every bean is
  `@ConditionalOnMissingBean`, so an app that declares its own still wins.

This is the one place in SB-Emulators that self-registers, under the framework-dedicated-module
exception to `R_no_spi_selfregister`; the rationale is
[D_library_servlet](../emulators/decisions.md#D_library_servlet).

## See also

- [`../guides/1-swing-to-emulators/host-app-spring-boot.md`](../guides/1-swing-to-emulators/host-app-spring-boot.md)
  — the migrator-facing setup for the Spring Boot lane.
- [`../emulators/README.md`](../emulators/README.md) — the library this module wires.
