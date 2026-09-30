# `swingbridge-emulators-jcalendar-1.4`

Provides [JCalendar](https://github.com/toedter/jcalendar) 1.4's `JDateChooser` on top of SB-Emulators, so a
migrated Swing app that used it keeps compiling and renders a native Vaadin date picker. A clean
reimplementation, not a fork of upstream code.

Not affiliated with or endorsed by the JCalendar project.

- **Migrating an app onto this?** → [`MIGRATION.md`](./MIGRATION.md) — the dependency swap, the
  import rule, what is and isn't ported, and the changes that go beyond the import swap. It ships
  inside the jar too, at `META-INF/emul/MIGRATION.md`.
- **Where the code came from, and under what licence** → [`PROVENANCE.md`](./PROVENANCE.md).
- **Which add-ons exist at all** →
  [`guides/1-swing-to-emulators/addons.md`](../../guides/1-swing-to-emulators/addons.md).
