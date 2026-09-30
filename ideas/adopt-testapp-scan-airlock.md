# adopt-testapp: an airlocked scan channel — validator-gated, not agent-reported

**Status:** **recorded, not adopted.** Proposed 2026-08-18 while running the `adopt-testapp` skill
against the inventory app, then explicitly parked ("record it as an idea, then let's ignore it for
now"). Nothing here is a decision. Step 2 currently uses the ordinary subagent report shape.

**Maintainer-facing.**

**Graduation:** if adopted, the mechanism lands in
[`../.claude/skills/adopt-testapp/SKILL.md`](../.claude/skills/adopt-testapp/SKILL.md) step 2
(plus a validator script beside `ingest.sh`), and this file is deleted. If rejected, the reasoning
in "Why it was parked" is the residue worth keeping — fold it into the skill's step 2 as a
one-paragraph note on why the report shape is what it is, then delete this file.

---

## The idea

Step 2's subagent reads untrusted third-party source. Its report then travels into the main agent's
context, which is the context that later drives migration. If the tree could poison the scanner, the
scanner's report is the delivery vehicle — so **don't let the report cross at all**. Let the only
thing that crosses be a machine-validated verdict with a bounded alphabet:

```
ADOPT-SCAN-V1
injection-findings: 0
callhome-findings: 2
config-findings: 1
unrecognized-deps: 0
verdict: CLEAN|FINDINGS|STOP
```

A `verify-scan.sh` beside `ingest.sh` matches that grammar and echoes **only capture groups** —
digits and a three-value enum. No prose can ride through.

## Why the Agent tool cannot implement it

A subagent's final message **is** the tool result: it enters the parent's context by construction,
and there is no supported way to suppress or rewrite it. Instructing the subagent to "return only a
fixed token" buys nothing — a poisoned agent returns something else, and by then it has been read.
(Whether a `PostToolUse` hook can filter a Task result is the one part worth verifying rather than
assuming.)

The boundary has to move from *agent* to *process*:

```fish
claude -p (cat scan-prompt.txt) > .../scan-report.md 2> .../scan-stderr.log
echo done                     # the only thing the main agent sees
bash verify-scan.sh .../      # the only channel that carries content
```

Redirected stdout means the Bash result carries nothing back. The airlock is then enforced by shell
redirection rather than by an agent's cooperation, which is the whole point.

## The one mistake that voids the whole design

`echo "invalid format: $line"`. One diagnostic that echoes its input turns the airlock into a pipe,
and the attacker's optimal move becomes *emit a malformed verdict file*. The validator must print
`INVALID` and nothing else — never the offending bytes, never a diff, never a `got: …`. Same for
stderr: capture it to a file nobody reads.

## Why it was parked

Three reasons, in increasing order of weight:

1. **It destroys the findings.** Run against the inventory scan, the channel yields
   `injection-findings: 0, callhome-findings: 2, verdict: FINDINGS` — and everything that made the
   scan worth running (a `0.0.0.0` listener, a dead back-door and *why* it is dead, an undecryptable
   `config.ini`, a second dead dependency, a closed open-question in the plan doc) stays on the far
   side. The warnings-ledger workflow the skill now uses — go through each warning one-by-one with
   the human — cannot run on a channel that transmits the number `3`.
2. **The licence grant cannot cross a narrow channel at all.** It is unbounded text from an
   untrusted file, and capturing it verbatim is a *required* output of step 2. Under this design it
   becomes a human's paste job — cheap at three lines per adoption, and arguably where it belongs,
   but it is a workflow cost the current shape does not have.
3. **It does not compose with the rest of the procedure.** Step 3 reads `pom.xml` by eye into the
   main context; step 7 and every migration round read all 64 files into it. Hermetically sealing
   step 2 while step 3 opens the same tree is a bank vault with a window beside it.

## The argument that survives, if this is ever revisited

Point 3 is the strongest objection and it has a real answer: the airlock is not protecting *the
context*, it is protecting **the go/no-go decision**. Later steps read the source only *because the
gate said proceed*, so the gate verdict is precisely the decision an attacker most wants to flip.
Making that one verdict unpersuadable-by-content is worth more than its bandwidth suggests.

That argues for a **hybrid** rather than either extreme, which is the shape to build if this is
picked up:

- **verdict channel** — validator-enforced, bounded alphabet, decides go/no-go only;
- **prose report** — written to disk, read by the main agent *only after* the verdict clears, and
  treated as data with the same status as the source it describes;
- **licence grant** — human-pasted.

## Practical caveats before building it

- A headless `claude -p` needs API network access, which the agent sandbox denies
  (`allowedHosts: []`) — it would need an explicit carve-out held open across the scan.
- It is a separate billed session rather than a subagent.
- The validator must be checked in beside `ingest.sh` and treated as security-relevant code: its
  failure mode is silent (it keeps working while relaying prose), so it wants a test with a
  malformed-input case asserting that **nothing** but `INVALID` is printed.

## See also

- [`../.claude/skills/adopt-testapp/SKILL.md`](../.claude/skills/adopt-testapp/SKILL.md) — step 2,
  the shape this would replace; and "When a gate finds something", the error/warning triage that
  makes the prose report load-bearing.
- [`../testapps/inventory/PROVENANCE.md`](../testapps/inventory/PROVENANCE.md) § Trust — the
  adoption that surfaced this, and the scan findings it records.
