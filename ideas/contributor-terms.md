# Contributor terms for a multi-lane repository

The one question [`D_licence_lanes`](../emulators/decisions.md#D_licence_lanes) left open.

- **`Q_contributor_terms`** — does a permissive lane change how outside contributions are accepted
  (CLA / DCO)? The repository now holds Apache-2.0, GPLv2+CE, LGPL-2.1 and BSD trees side by side,
  and an incoming patch lands in exactly one of them. Vaadin's other Apache repos presumably have a
  house answer; adopt it rather than invent one.

**Deadline: first publish**, for the same reason as the lanes themselves — before any outside
contribution is accepted, the question binds nobody.

Whatever the answer, `R_gpl_provenance` rule 5 (never paste third-party code into an SB-Emulators
file) stands, and an Apache lane is exactly when it gets tested: incoming Apache-2.0 code becomes
*easier* to accept, into the Apache trees, and never into the GPL ones.
