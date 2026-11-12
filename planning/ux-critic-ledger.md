# UX critic ledger

One row per ux-critic finding. The PM has the dispositions recorded here as the user decides (rules mode of ux-reviewer). The critic reads it first and does not repeat a row in any status except `REWORK`, where it may only propose a different solution.

| ID | Pattern (keywords) | Round | Status | Link |
|---|---|---|---|---|

Status values:
- `PROPOSED`: reported by the critic, not decided yet.
- `IN REVIEW`: accepted in chat, built and merged, not yet approved in the emulator. Its `05` entry is marked pending.
- `ADOPTED`: approved by the user in the emulator (link: R-## or the `05` section).
- `REWORK`: seen in the emulator and not approved; the row records what was tried and what the user disliked.
- `REJECTED`: refused by the user (also copied to `05` §8 "Decided — do not re-propose").
- `DEFERRED`: postponed (link: E9 row or round).

A round that changes tokens or components does not start while the previous such round has rows `IN REVIEW`.
