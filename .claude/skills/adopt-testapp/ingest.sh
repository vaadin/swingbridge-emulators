#!/usr/bin/env bash
#
# Mechanical ingest for the adopt-testapp skill — the whole of what used to be
# steps 1, 2 and 3, in one invocation:
#
#   clone once  ->  pin the exact SHA  ->  destroy upstream .git  ->  re-init OUR
#   history over the pristine tree  ->  delete the build/meta surface by path
#
# Run it as:   bash .claude/skills/adopt-testapp/ingest.sh <repo-url>
#
# WHY A SCRIPT AND NOT A CHECKLIST. Every action here is mechanical — path
# matching, no judgement — and all of it must happen BEFORE anything reads this
# tree. A script cannot be prompt-injected; an agent working through a prose
# checklist reads helpfully (the README for the licence, the pom for a
# coordinate) and walks into an injection while trying to follow the procedure
# well. So: this script reads NO file body, ever. The only files it opens are
# .git ref files, to pin the SHA. The first thing allowed to read this tree is
# the subagent scan in skill step 2.
#
# It is safe to re-run: if the clone is already there it skips straight to the
# reduction, which is idempotent. It never deletes the scratch root and never
# re-clones over an existing tree — if you want a different app, wipe the root
# by hand first. That is deliberate: "rm -rf the root" stays a human act, so no
# agent resuming mid-procedure can destroy the pinned SHA or the licence grant.
set -euo pipefail

# The repo root, derived from this script's own location (.claude/skills/adopt-testapp/) so no
# cwd can point it elsewhere; the guard below still checks that it is the SB-Emulators tree.
REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
ROOT=$REPO/temp/new-testapp
APP=$ROOT/imported-app
NOTES=$ROOT/adoption-notes.md

die()  { printf '\n!! %s\n' "$*" >&2; exit 1; }
head2() { printf '\n== %s\n' "$*"; }

# ---------------------------------------------------------------- guards
[[ -d $REPO/emulators && -d $REPO/surrogates ]] \
  || die "$REPO does not look like the SwingBridge Emulators repo — refusing to touch anything."
# Name the exact path we are about to create, not a bare "temp": a `/temp/` rule
# in .gitignore matches directories only, and check-ignore on a not-yet-existing
# "temp" cannot tell that it is one — so the bare form reports "not ignored" on a
# correctly-configured repo and the guard never passes.
git -C "$REPO" check-ignore -q "$ROOT" \
  || die "$ROOT is not gitignored in $REPO — fix .gitignore before ingesting untrusted code."

URL=${1:-}
[[ -n $URL ]] || die "usage: bash $0 <repo-url>"
[[ $URL == https://* ]] || die "refusing: pass a plain https:// clone URL (got: $URL)"
[[ $URL != *' '* ]]     || die "refusing: whitespace in URL"

# ---------------------------------------------------------------- phase 1: clone + pin + de-git
if [[ -e $APP ]]; then
  head2 "phase 1/3 clone — SKIPPED, $APP already exists (resuming)"
  [[ -d $APP/.git ]] || die "clone exists but has no history: step 2 never ran and cannot be
     re-run safely (the upstream SHA is only in $NOTES now). Inspect by hand."
  git -C "$APP" log --oneline | tail -1 | grep -q '^[0-9a-f]* ingest ' \
    || die "the history in $APP was not created by this script (root commit is not an 'ingest'
     commit). Refusing to touch it."
  echo "   history root: $(git -C "$APP" log --oneline | tail -1)"
else
  [[ -e $ROOT ]] && die "scratch root $ROOT exists but holds no clone.
     One adoption at a time: finish or abandon it, then 'rm -rf $ROOT' BY HAND and re-run.
     This script will not delete the root — that stays a human act."
  head2 "phase 1/3 clone, pin the SHA, destroy upstream .git"
  mkdir -p "$ROOT"
  # --depth 1: history is useless to a testbed baseline. --no-tags: fewer refs.
  # No --recurse-submodules, deliberately: an unfetched submodule is code we
  # never pulled and therefore never have to trust. A later build failing on a
  # missing submodule dir is a finding for the human, not something to fix by
  # fetching it.
  git clone --depth 1 --no-tags "$URL" "$APP"

  # The SHA, read as a plain file — not a git command, and exact: what we
  # actually received, not what the remote is serving a moment later.
  ref=$(sed -n 's/^ref: //p' "$APP/.git/HEAD")
  if [[ -n $ref && -f $APP/.git/$ref ]]; then
    SHA=$(cat "$APP/.git/$ref")
  else
    SHA=$(awk -v r="$ref" '$2==r {print $1}' "$APP/.git/packed-refs")
  fi
  [[ ${#SHA} -eq 40 ]] || die "could not read a 40-char SHA (ref='$ref')"
  BRANCH=${ref#refs/heads/}
  echo "   url:    $URL"
  echo "   branch: $BRANCH"
  echo "   SHA:    $SHA"

  # Destroy upstream .git IMMEDIATELY, before any other command touches the
  # tree. Git has a real history of malicious repos achieving code execution
  # during ordinary commands (CVE-2021-21300 via plain `git checkout`). The
  # clone is unavoidable exposure; every later git invocation is exposure we
  # avoid for free. `rm` is not a git command.
  rm -rf "$APP/.git" "$APP/.githooks"

  # ------------------------------------------------------------- phase 2: our own history
  head2 "phase 2/3 re-init a clean, local-only history over the PRISTINE tree"
  # `git -C`, never `cd`: cwd persists between commands in the agent harness,
  # so one forgotten `cd` re-points every later deletion at the repo root.
  git -C "$APP" init -q
  # -f is load-bearing: without it upstream's .gitignore silently shapes OUR
  # archive, and a checked-in lib/*.jar or seeded *.db would never enter the
  # commit that three later steps treat as the recoverable copy of everything.
  git -C "$APP" add -A -f
  git -C "$APP" commit -q -m "ingest $(basename "$URL") @ $SHA from $URL"
  tracked=$(git -C "$APP" ls-files | wc -l)
  ondisk=$(find "$APP" -type f -not -path "$APP/.git/*" | wc -l)
  [[ $tracked -eq $ondisk ]] \
    || die "pristine commit is incomplete: $tracked tracked vs $ondisk on disk."
  echo "   root commit: $(git -C "$APP" log --oneline)"
  echo "   $tracked files committed == $ondisk on disk (archive is complete)"
  printf '%s\n' "$URL" "$SHA" "$BRANCH" > "$ROOT/.pinned"
fi

# ---------------------------------------------------------------- phase 3: mechanical reduction
head2 "phase 3/3 mechanical reduction (path-based only — no file is read to decide)"

# Recorded, not deleted: the licence grant is often an informal "credit me" line
# inside the README, and step 2's scan must clear that file before anyone reads
# it. Skill step 2 captures the grant, then deletes the README; licence files stay in
# the tree, since testapps ship in the distribution kit.
KEPT=$(find "$APP" -maxdepth 1 \( -iname 'readme*' -o -iname 'license*' -o -iname 'licence*' \
        -o -iname 'copying*' -o -iname 'notice*' \) -printf '%P\n' | sort)

# Tests are a gate, not a deletion: usually there are none, but a substantial
# suite is a decision for the human, so this script never removes them.
TESTS=$(find "$APP" \( -path '*/src/test/*' -o -name '*Test.java' -o -name '*Tests.java' \
        -o -name '*IT.java' \) -type f -printf '%P\n' | sort)

rm_if() { for t in "$@"; do [[ -e $APP/$t ]] && { echo "   rm $t"; rm -rf "$APP/$t"; }; done; true; }

echo " -- Maven/Gradle wrapper + .mvn/ (fires before a line of app source compiles:"
echo "    a hostile distributionUrl runs an arbitrary Maven, .mvn/extensions.xml loads"
echo "    ahead of every plugin — earliest-executing files in the tree)"
rm_if mvnw mvnw.cmd .mvn gradlew gradlew.bat gradle/wrapper

echo " -- agent-instruction files (highest-value injection vector: already formatted"
echo "    as instructions to an agent, and loaded automatically by some tools)"
rm_if AGENTS.md CLAUDE.md GEMINI.md .cursorrules .cursor .claude .aider.conf.yml .windsurfrules

echo " -- CI configs (.github/ also covers copilot-instructions.md)"
rm_if .github .travis.yml .gitlab-ci.yml Jenkinsfile appveyor.yml azure-pipelines.yml .circleci circle.yml

echo " -- git meta (.gitignore: once vendored it would keep governing what OUR git"
echo "    tracks in that subtree; .gitattributes: filters)"
rm_if .gitattributes .gitignore .githooks

echo " -- IDE metadata"
rm_if .idea .project .classpath .settings .vscode
find "$APP" -name '*.iml' -o -name '*.iws' -o -name '*.ipr' | while read -r f; do
  echo "   rm ${f#"$APP"/}"; rm -f "$f"; done

echo " -- build extras at the app root (loose scripts, Makefile)"
find "$APP" -maxdepth 1 \( -name '*.sh' -o -name 'Makefile' -o -name 'makefile' \) \
  | while read -r f; do echo "   rm ${f#"$APP"/}"; rm -f "$f"; done

echo " -- docs, screenshots, wikis (all recoverable from the root commit;"
echo "    README/licence files are KEPT for the step-2 scan)"
rm_if snapshots screenshots doc docs wiki site
find "$APP" -iname '*.md' -not -iname 'readme*' | while read -r f; do
  echo "   rm ${f#"$APP"/}"; rm -f "$f"; done
find "$APP" -maxdepth 1 \( -iname '*.png' -o -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.gif' \) \
  | while read -r f; do echo "   rm ${f#"$APP"/}"; rm -f "$f"; done

if git -C "$APP" status --porcelain | grep -q .; then
  git -C "$APP" add -A -f
  git -C "$APP" commit -q -m "reduce: build + meta surface (wrapper, .mvn, agent files, CI, git meta, IDE, docs)"
  echo "   reduction commit: $(git -C "$APP" log --oneline -1)"
else
  echo "   nothing left to remove (reduction already applied)"
fi

# ---------------------------------------------------------------- report
head2 "state after ingest"
echo "   files remaining: $(find "$APP" -type f -not -path "$APP/.git/*" | wc -l)"
echo "   .java files:     $(find "$APP" -name '*.java' | wc -l)"
echo "   root entries:    $(find "$APP" -maxdepth 1 -mindepth 1 -not -name .git -printf '%P\n' | sort | tr '\n' ' ')"
echo
echo "   KEPT for the step-2 scan (do NOT read these yet):"
[[ -n $KEPT ]] && printf '     %s\n' $KEPT || echo "     (none — no README or licence file in this repo)"
echo
if [[ -n $TESTS ]]; then
  echo "   !! GATE: $(printf '%s\n' "$TESTS" | wc -l) test file(s) found and NOT deleted."
  printf '     %s\n' $TESTS | head -20
  echo "     Record the count in the notes and decide with the human: delete, or flag for"
  echo "     test-mining as a separate activity. Do not block adoption on it."
else
  echo "   tests: none in this repo (nothing matching src/test/**, *Test.java, *Tests.java, *IT.java)"
fi

head2 "NEXT"
if [[ -f $NOTES ]]; then
  echo "   $NOTES exists — left untouched. Update its checklist by hand."
else
  echo "   Write $NOTES now: URL + SHA (see $ROOT/.pinned), clone date, a checklist of the"
  echo "   remaining steps, this reduction inventory, and a Trust placeholder."
fi
echo "   Then skill step 2: the prompt-injection + call-home scan, IN A SUBAGENT."
echo "   Until that gate clears, the subagent is the only reader of this tree —"
echo "   no by-eye read of the README, the pom, the config or any source."
