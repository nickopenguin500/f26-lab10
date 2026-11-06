# RACE.md

One section per milestone. Fill each one in order as you go.

The prediction in milestone 1 is written BEFORE you run your race test. A TA
may ask about it. Commit it on its own so the order is visible.

Keep it short and specific. Point at methods, call sites, and the invariant.

---

## Milestone 1: from the ticket to a test that fails every time

### Suspect list

Everything your review turned up that touches shared state, with a one-line
verdict for each. Add or remove rows as needed.

| # | What you found (method, field, or class) | Verdict, in one line |
|---|---|---|
| 1 | `BookingService.sequence` (`AtomicLong`) | Thread-safe: `incrementAndGet()` guarantees unique IDs across threads without race conditions. |
| 2 | `BookingService.catalog` (`Map<String, Room>`) | Safe: populated in constructor and never modified; read-only access is thread-safe. |
| 3 | `BookingService.stats` (`BookingStats`) | Unsynchronized counters lead to lost increments, but benign because stats are documented as approximate and unused in logic. |
| 4 | `InMemoryBookingStore.bookings` (`List<Booking>`) | Thread-safe: private list where every store interface method is `synchronized`. |
| 5 | `BookingService.book` (check-then-act) | Race condition: calls `hasOverlap` then `insert` without synchronization across the pair, violating the single confirmed booking invariant. |
| 6 | `BookingService.cancelBooking` | Safe: calls `store.cancelAndPromote`, which atomically cancels and promotes under a single synchronized block. |

**Which one explains the ticket, and how you know.** Tie it to the invariant
the ticket names.
Suspect 5 (`BookingService.book`). The invariant is: "A room can have at most one CONFIRMED booking covering any given minute." Because `store.hasOverlap` and `store.insert` are two separate synchronized calls, there is an unsynchronized window between checking and inserting. Two concurrent `book` requests for the same room and slot can both check `hasOverlap` before either inserts. Both observe `false`, proceed to `insert`, and create two confirmed bookings for the same room and time.

### Predicted interleaving (write this before you run your race test)

Two threads, one row per step, in the form from lecture. Describe what each
thread does at that step and leave the other column empty. Add or remove rows
as your prediction needs.

| Step | Thread A | Thread B |
|---|---|---|
| t1 | Calls `service.book("WEH-5409", 540, 600)`, executes `store.hasOverlap(...)`, returns `false`. Parks at `PausingStore` gate. | |
| t2 | | Calls `service.book("WEH-5409", 540, 600)`. |
| t3 | | Executes `store.hasOverlap(...)`, returns `false`. |
| t4 | | Creates `bk-1` and calls `store.insert(bk-1)` with status `CONFIRMED`. |
| t5 | | Completes `book(...)` returning confirmed `bk-1`. |
| t6 | Gate released. Thread A resumes with its cached result (`false`). | |
| t7 | Thread A creates `bk-2` and calls `store.insert(bk-2)` with status `CONFIRMED`. | |
| t8 | Thread A completes `book(...)` returning confirmed `bk-2`. | |
| | **End state:** | Store contains two CONFIRMED bookings (`bk-1` and `bk-2`) for room WEH-5409 in slot 540..600. |

If the run turned out different from your prediction, add one line on what
you missed. Leave the table as you first wrote it.

### Your race test

**How you aimed `PausingStore`, and the result.** Method name and call index, why
that pair pins the schedule you predicted, and the output of two separate
runs (assertion failure and log lines).

---

## Milestone 2: the patch a teammate sent

**Your milestone-1 test on the patched code.** Green or red. Paste the result.

**The re-aimed run.** Method name, call index, the pasted result, and one line
on why that aim tests the patch rather than sidestepping it.

**Your verdict.** Accept or reject, and what your milestone-1 result does and
does not show about the patch.

---

## Milestone 3: the fix you would ship

**Which option, and what you changed.** Name the lock or the atomic operation
and the two operations it now covers. Paste the diff of your change and the
passing run of your milestone-1 test and the full suite.

**Is your milestone-1 test still testing anything?** After your fix, describe
what that test now exercises. Did `PausingStore` pause a call during the green run? How do
you know?

**What runs one at a time now that ran in parallel before.** Name concretely which callers
queue behind which. "Nothing, it is just correct now" does not count as an
answer.

**What you left unfixed.** For anything your review flagged that you chose not
to fix, one line each on why.
