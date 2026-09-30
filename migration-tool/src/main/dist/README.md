# SwingBridge Emulators — migration tools

The three runnable tools a Swing→emulators migration uses, one per **mechanical** phase of the
migration guide. Unzip, and run them from this folder:

```bash
bin/hazard-scan   <app>/src/main/java <app>/src/main/resources --report hazards.md
bin/static-sweep  <app>/target/classes --lib <app>/target/dependency --report static-sweep.md
bin/import-swap   <app>/src/main/java --dry-run --report import-swap.md
```

On Windows, the `.cmd` twins take the same arguments. A JDK is the only prerequisite — no Maven, no
network. `JAVA_HOME` is used if set, otherwise `java` off the `PATH`.

| phase | script | what it does |
|---|---|---|
| 1 — pre-flight | `bin/hazard-scan` | finds the known migration hazards in a source tree and writes them up grouped by hazard |
| 1 — `static` sweep | `bin/static-sweep` | builds the `static`-field worklist from the app's **compiled classes**, before anything is rewritten |
| 2 — import rewrite | `bin/import-swap` | rewrites `java.awt` / `javax.swing` / `java.util` / `java.text` references onto their `vaadinx` emulators |

Options, all three: `--report <path>` writes the Markdown report to a file instead of stdout. The
two source tools take `--table <tsv>` to add a table off disk; `hazard-scan` also takes `--list`
(print the effective pattern table and exit), and `import-swap` takes `--dry-run` (report without
editing a single file). `static-sweep` takes `--lib <dir>` / `--cp <path>` (the app's own
dependencies, so a declared type can be followed up its hierarchy), `--diff <classes-dir>` (what
became of each row, once the migration has run) and `--src <dir>` (warn when the classes are older
than the sources).

**Point `static-sweep` at the app you have not migrated yet.** It reads class files, not source, so
build the app first (`mvn compile`) and give it the output directory. A dependency it is not handed
costs only precision: the report says which types went unresolved, and an unresolved row is never
demoted out of the worklist.

## Exit codes

**0 means the tool ran** — its report is an input to your triage, never a verdict. A hazard hit, a
declined file, a full `static` worklist, a tree that needed no changes: all of them exit 0, and none
of the three is a build gate. (The gate that *is* one comes later and separately: your migrated
app's own test suite calls `MigrationGuardrails`, which throws.)

**Non-zero means it could not run**: bad arguments, an unreadable file, no swap table on
`import-swap`'s classpath, or no class files under the directory `static-sweep` was given. The last
two are loud on purpose and their messages name the fix — a rewrite of nothing or an empty worklist
reported as success is the one failure shape these tools refuse to have.

## This zip ships the engine; the swap table travels with the emulators

`bin/hazard-scan` works as it is: its pattern table is inside the tool's own jar. So does
`bin/static-sweep`, which has no table at all — its input is your app's class files, which the
compiler already resolved.

`bin/import-swap` has **no compiled-in knowledge of Swing**. It unions every
`META-INF/emul/ported-types.tsv` it finds on its classpath — which is every jar in `lib/` — so what
is in `lib/` decides what gets rewritten. This zip carries no `swingbridge-emulators` jar, because
the swap table belongs to the emulators version you are migrating onto. Drop it in:

```bash
mvn -q dependency:copy -Dartifact=com.vaadin.swingbridge:swingbridge-emulators:<version> \
    -DoutputDirectory=lib
```

…and one more per [add-on](https://github.com/vaadin/swingbridge-emulators) your app uses, e.g.
`com.vaadin.swingbridge:swingbridge-emulators-jcalendar-1.4:<version>` or
`com.vaadin.swingbridge:swingbridge-emulators-jgoodies-forms-1.2.1:<version>`. If you got this zip inside the
**distribution kit**, `lib/` already has all three and there is nothing to fetch.

Until a table-carrying jar is there, `bin/import-swap` exits non-zero with

```
import-swap: no META-INF/emul/ported-types.tsv found on the classpath and none given with --table. …
```

which is the whole reason the wildcard classpath is the launcher here and `java -jar` never is:
`-jar` ignores `-cp`, so the tables would be invisible and the failure silent.

## More

Source, guides, decision logs and issue tracker:
<https://github.com/vaadin/swingbridge-emulators>.
