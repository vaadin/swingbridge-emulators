# `swingbridge-emulators-jgoodies-forms-1.2.1`

Provides JGoodies Forms 1.2.1's `FormLayout` on top of SB-Emulators, so a migrated Swing app that laid its
forms out with it keeps compiling — and renders as CSS Grid rather than collapsing into a vertical
stack. A fork of BSD-licensed upstream sources with the pixel layout engine replaced by a CSS
emitter.

Forked from JGoodies Forms 1.2.1, copyright (c) 2002-2008 JGoodies Karsten Lentzsch, under the
3-clause BSD licence in [`LICENSE`](./LICENSE). **Not affiliated with, endorsed by, or supported by
JGoodies.** Report problems with this fork here, never upstream.

- **Migrating an app onto this?** → [`MIGRATION.md`](./MIGRATION.md) — the dependency swap, both
  import rules (one of them a *delete*), what is and isn't ported, and the changes that go beyond the
  import swap. It ships inside the jar too, at `META-INF/emul/MIGRATION.md`.
- **What was forked, what was changed, and the licence obligations** →
  [`PROVENANCE.md`](./PROVENANCE.md).
- **Which add-ons exist at all** →
  [`guides/1-swing-to-emulators/addons.md`](../../guides/1-swing-to-emulators/addons.md).
