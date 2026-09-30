# `/guide-docfix inventory`

`inventory` is an **adopted** app that leans on two third-party add-ons, so its stumbles land on
more owners than core's own guides. Getting the routing right is most of the work.

## Required reading

`testapps/inventory/PROVENANCE.md`. It tells you which app behaviours are upstream bugs ported on
purpose, so you don't "fix" a doc to explain away a stumble that is really the app being faithful to
itself. `/guide-migrateapp` denies it to the migrating agent; docfix needs it.

## Entries not to act on

**One Phase-0 entry in run 4's list is factually wrong:** the `(c)`-note claiming *the app ships
Eclipse metadata*. It does not — `adopt-testapp` stripped upstream's, and `.classpath` / `.project` /
`.settings` are gitignored. Those files were written into `work/inventory` by **m2e**, during the
language-server import the pre-flight triggered. The guide's warning about JDT compiling into
`target/` is right and stays; the attribution to the app is not.

## Extra owners

Two rows join step 4's table:

| the stumble is about | fix goes in |
|---|---|
| *whether* an add-on exists, which coordinate replaces which, or how to get at an add-on's instructions | `guides/1-swing-to-emulators/addons.md` — **the index only** |
| *how to use* a specific add-on: its covered surface, what it drops, a behavioural difference, a per-library gotcha | `third-party/<module>/MIGRATION.md` — the add-on's own addendum |

**The index/addendum split is [M1D_addon_migration_docs](../../../migration/1-swing-to-emulators/decisions.md), and violating it is the easiest mistake here.** An add-on fact never goes in core docs, and core never restates an addendum: `addons.md` changes when SB-Emulators *gains or loses* an add-on, an add-on's `MIGRATION.md` changes when that add-on changes. A stumble reading "the docs didn't tell me `PanelBuilder` is missing" fixes `third-party/jgoodies-forms-1.2.1/MIGRATION.md`, not `guide.md` — even though the migrator hit it while following `guide.md`.

**Backport.** An add-on `MIGRATION.md` fix has no `guides/` counterpart and normally no spec
counterpart either — the addendum *is* the source of truth for its own library. It backports only
when the finding is about the add-on **model** rather than the library (e.g. "an addendum should
always state X"), which lands in `M1D_addon_packaging`–`M1D_addon_migration_docs`.

**Stage-1 changes.** A relaxation that lands in `swing/` gets a row in `PROVENANCE.md` § "Changed
since adoption".

**The stage-1 pom.** **Never** put an add-on coordinate in `testapps/inventory/swing/pom.xml`: its
upstream `com.toedter:jcalendar` / `com.jgoodies:forms` are the add-on-discovery probe
([`guide-migrateapp/inventory.md`](../guide-migrateapp/inventory.md) § "What makes this one different").

## Tests after applying

**If you edited `addons.md`, re-run the add-on modules' tests.** Each add-on's `MigrationDocTest`
asserts that `addons.md` names both its Maven artifactId and the upstream `groupId:artifactId` it
replaces, so a reworded row can fail that module's build:

```bash
./mvnw -C -pl third-party/jcalendar-1.4,third-party/jgoodies-forms-1.2.1 test
```

## See also

- `migration/1-swing-to-emulators/decisions.md` — `M1D_addon_packaging`–`M1D_addon_migration_docs`:
  the add-on packaging model, the swap-vs-reimplement triage, and the index/addendum contract the
  extra owners enforce.
