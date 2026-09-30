# The file-backed database, behind `-Pfile-db`

`src/main/resources/hibernate.cfg.xml` is **in-memory** — the app seeds itself, so a throwaway
database is the sane default and nothing has to survive a restart. The `hibernate.cfg.xml` next to
this README is upstream's file-backed URL (`./inventoryApp.db` in the working directory), and the
`file-db` profile points `testResources` at this directory so it lands in `target/test-classes` and
shadows the main copy on the test-scope classpath.

It is the only way to exercise `hbm2ddl.auto=update` against a **pre-existing** schema, which an
always-fresh in-memory database never reaches. See the profile in
[`../../../pom.xml`](../../../pom.xml) and the fixture section of
[`../../../../PROVENANCE.md`](../../../../PROVENANCE.md) — including why `clean` is not optional when
switching modes.
