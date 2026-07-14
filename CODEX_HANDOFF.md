# Codex handoff

This file carries useful context from the Codex task rooted at
`/Users/dannysaleeb/dev/supercollider/VoxBoxCodex` into future tasks rooted at
`/Users/dannysaleeb/dev/supercollider/VoxBox`.

## Start the next task with this

> Work in `/Users/dannysaleeb/dev/supercollider/VoxBox`. Read `AGENTS.md`,
> `CODEX_HANDOFF.md`, and `README.md` before making changes. The former
> `VoxBoxCodex` directory was a linked Git worktree and may have been removed;
> do not assume it still exists. Confirm the current branch and worktree status
> before editing, preserve unrelated user changes, and run the smoke tests
> relevant to anything you change.

## Verified state at handoff

On 2026-07-14:

- `/Users/dannysaleeb/dev/supercollider/VoxBox` was the primary worktree on
  `master`.
- `/Users/dannysaleeb/dev/supercollider/VoxBoxCodex` was a linked worktree on
  branch `VoxBoxCodex`.
- Both branches pointed to commit `7bd57ff` (`added VoxFixedVelocity`).
- `master` matched `origin/master`.
- `VoxBoxCodex` appeared one commit ahead of `origin/VoxBoxCodex` only because
  that remote branch still pointed to the preceding commit; its current commit
  was already the same commit as `master`.
- The linked worktree had no tracked uncommitted changes before these handoff
  files were created.
- Its only pre-existing untracked item was a `VoxBox` symlink whose target was
  written as `./dev/supercollider/VoxBox`; it should not be treated as project
  content.

Re-check all of this with `git status`, `git log`, and `git worktree list`; this
section is a historical snapshot, not an instruction to reset current state.

## Worktree migration

Codex task history is stored separately from the Git worktree. Removing the
linked worktree should not remove the original task from the Codex sidebar, but
the old task remains bound to its original working-directory path. Continue
work in a new task opened at the primary `VoxBox` directory.

After ensuring these handoff files are available from `master`, close the task
using `VoxBoxCodex` and remove the linked worktree from outside it with:

```sh
git -C /Users/dannysaleeb/dev/supercollider/VoxBox \
  worktree remove --force \
  /Users/dannysaleeb/dev/supercollider/VoxBoxCodex
```

The local `VoxBoxCodex` branch may then be deleted if it is fully merged and no
longer needed:

```sh
git -C /Users/dannysaleeb/dev/supercollider/VoxBox branch -d VoxBoxCodex
```

Do not delete the folder directly unless Git's worktree metadata has already
been cleaned up.

## Historical-context boundary

This handoff records the project state and discussion visible to the task when
it was written. A new Codex task does not automatically inherit the original
conversation. Keep the original task in the sidebar for human reference. The
conversation covered the safe move from the linked `VoxBoxCodex` worktree to
the primary `VoxBox` worktree; no additional implementation work from the
conversation was pending at the time of this handoff.

## Project direction

`README.md` is the detailed working map. In brief, VoxBox is a working prototype
for importing MIDI, transforming metre-aware labelled musical material through
live `VoxNode` graphs, gathering stable snapshots into boxes or banks, arranging
multi-voice results, preserving descriptive provenance, and exporting toward
notation interchange. Current priorities are consolidation of contracts,
repeatable tests, help documentation, and a dependable export path rather than
unbounded growth of the live-coding DSL.
