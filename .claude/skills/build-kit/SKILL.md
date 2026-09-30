---
name: build-kit
description: Rebuild the whole SB-Emulators reactor clean, then unpack the distribution kit zip into a fresh temp folder outside any git repository and report it as SWINGBRIDGE_HOME — the step before anything that consumes SB-Emulators as artifacts (a guide-loop round, a testapp build, an agent handed a build command). Run by /guide-migrateapp; also standalone.
---

## Procedure

**1. Rebuild the reactor.**

```bash
./mvnw -C clean install
```

One command, from the repository root. It installs every module's SNAPSHOT into `~/.m2` — which is
what a migrated app resolves by coordinate — **and** assembles the distribution kit, both as
`zip-distro/target/swingbridge-emulators-<version>-dist.zip` and unpacked under
`zip-distro/target/unzipped/` (which `KitLayoutIT` and `KitSmokeIT` read; nothing downstream of this
skill uses it).

Not `-pl … -am`, and not incremental. The rebuild is cheap and bounded; recovering from a stale
artifact is neither, and it fails *misleadingly* — an agent downstream reports a stumble against its
own work rather than "my toolchain was stale".

**2. Unpack the zip into a fresh temp folder, outside any git repository.**

```bash
KIT_PARENT=$(mktemp -d "${TMPDIR:-/tmp}/swingbridge-kit.XXXXXX")
unzip -q zip-distro/target/swingbridge-emulators-*-dist.zip -d "$KIT_PARENT"
ls -d "$KIT_PARENT"/swingbridge-emulators-*
```

**That inner folder is `SWINGBRIDGE_HOME`** for whatever runs next. Report its absolute path when
you are done.

Out of the repository, deliberately, for two reasons that are one fact each. **No `.gitignore`
reaches it** — a kit under `zip-distro/target/` is ignored by this repository, and in Claude Code a
gitignored file has no LSP result locations, which is what made three rounds record a
"references are dead" finding that was never about `jdtls` (M1D_lsp_recommended). And **the
migrating agent cannot walk up into this repository** — no `../../..` reaching the emulator sources
or the decision logs a customer would not have.

**3. Clear the language server's workspace for that folder name, if you have `jdtls` installed.**

```bash
rm -rf ~/.cache/jdtls/jdtls-$(printf '%s' "swingbridge-emulators-<version>" | sha1sum | cut -c1-40)
```

`jdtls.py` keys its workspace on `sha1(basename(cwd))` and the Claude Code plugin passes no `-data`,
so **every kit of the same version shares one workspace** — and a workspace remembering projects
whose `.project` files the rebuild deleted hangs a fresh client at `initialize` outright (measured
twice). A restart is not a reset. Skip this if no `~/.cache/jdtls` exists.

**It needs a writable `~/.m2/repository`.** Under a sandbox that denies it, `install` fails on the
parent pom with `Read-only file system` before a single module compiles. Run it with the sandbox
disabled for that one command; do not downgrade it to `verify`, because `install` is the half that
refreshes the artifacts a migration resolves by coordinate.

## Treat a non-zero exit as a stop

Anything wrong upstream reaches the next consumer as a build failure it will mis-attribute to
itself. **The tell is always the same: failures in code nobody touched.** Three stale-tree modes,
all observed, all cured by re-running this skill once the cause is gone:

- **A concurrent build in the same working tree.** Another Claude Code session, an IDE, or the
  `jdtls` language server (`m2e` maps JDT's output onto `target/classes`, the same directories
  Maven uses) writing `target/` while Maven runs. It surfaces as a wave of `NoClassDefFoundError`
  or `cannot access <class>` in modules the build had just compiled — 124 errors in `:surrogates`
  on one run, a `testCompile` failure in `:migration-tool` on the next, from the same tree
  (2026-09-08). **Make sure nothing else is building this tree**, before and for as long as the
  downstream run lasts; a rebuild *during* a round re-poisons `target/` and the migrating agent
  meets a failure it never caused with no way to recognise it.
- **An IDE build poisoned `target/`.** Eclipse / JDT compiles into `target/classes`, and JDT rejects
  some code `javac` accepts (`SJSpinner:476` carries a standing comment about one such false
  positive); Surefire then reports errors whose message reads `Unresolved compilation problem` — a
  diagnostic baked into a `.class` file, which `javac` never produces.
- **A stale artifact in `~/.m2`.** Yesterday's `third-party/` add-on jars are exactly what a
  migration resolves by coordinate; `install` is what makes the coordinate mean today's code.

## What this is not

- Not the *shipped* testapp build. `clean install` does rebuild every testapp stage in place under
  `testapps/` (so none keeps a stale `target/`), but `./mvnw -C install -Pkit` is what builds the
  shipped copies out of the kit, which is what CI runs, and that invoker run is all `-Pkit` adds.
- Not a substitute for reading the failure. If it is red in code you *did* touch, that is yours.
