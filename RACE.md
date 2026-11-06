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
None; the test executed exactly as predicted.

### Your race test

**How you aimed `PausingStore`, and the result.** Method name and call index, why
that pair pins the schedule you predicted, and the output of two separate
runs (assertion failure and log lines).

- **Method name:** `hasOverlap`
- **Call index:** `1`
- **Why this pair pins the schedule:** Aiming at `hasOverlap` call 1 pauses Thread A immediately after its overlap check returns `false`, before it can call `insert`. While Thread A is held, Thread B calls `book`, checks `hasOverlap` (call 2, which does not pause), sees the slot still empty, and inserts its confirmed booking (`bk-1`). When Thread A is released, it resumes and inserts its confirmed booking (`bk-2`) without re-checking. This deterministically produces two confirmed bookings for the same room and slot.

#### Run 1 output:
```
2026-11-06T00:40:57.233Z INFO booking.confirmed booking=bk-1 room=WEH-5409 start=540 end=600
2026-11-06T00:40:57.235Z INFO booking.confirmed booking=bk-2 room=WEH-5409 start=540 end=600
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.025 s <<< FAILURE! -- in edu.cmu.cs214.lab10.harness.RaceTest
[ERROR] edu.cmu.cs214.lab10.harness.RaceTest.twoConcurrentBookingsForSameSlotProduceOnlyOneConfirmed -- Time elapsed: 0.017 s <<< FAILURE!
org.opentest4j.AssertionFailedError: expected: <1> but was: <2>
```

#### Run 2 output:
```
2026-11-06T00:41:02.003Z INFO booking.confirmed booking=bk-1 room=WEH-5409 start=540 end=600
2026-11-06T00:41:02.005Z INFO booking.confirmed booking=bk-2 room=WEH-5409 start=540 end=600
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.025 s <<< FAILURE! -- in edu.cmu.cs214.lab10.harness.RaceTest
[ERROR] edu.cmu.cs214.lab10.harness.RaceTest.twoConcurrentBookingsForSameSlotProduceOnlyOneConfirmed -- Time elapsed: 0.017 s <<< FAILURE!
org.opentest4j.AssertionFailedError: expected: <1> but was: <2>
```

---

## Milestone 2: the patch a teammate sent

**Your milestone-1 test on the patched code.** Green or red. Paste the result.

**Result: Green**
```
2026-11-06T00:41:15.692Z INFO booking.confirmed booking=bk-1 room=WEH-5409 start=540 end=600
2026-11-06T00:41:15.695Z INFO booking.rejected room=WEH-5409 start=540 end=600 reason=slot already held
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.025 s -- in edu.cmu.cs214.lab10.harness.RaceTest
[INFO] BUILD SUCCESS
```

**The re-aimed run.** Method name, call index, the pasted result, and one line
on why that aim tests the patch rather than sidestepping it.

- **Method name:** `hasOverlap`
- **Call index:** `2`
- **Result: Red (Fails deterministically)**
```
2026-11-06T00:41:30.971Z INFO booking.confirmed booking=bk-1 room=WEH-5409 start=540 end=600
2026-11-06T00:41:30.974Z INFO booking.rejected room=WEH-5409 start=540 end=600 reason=slot already held
2026-11-06T00:41:30.980Z INFO booking.confirmed booking=bk-2 room=WEH-5409 start=540 end=600
2026-11-06T00:41:30.980Z INFO booking.confirmed booking=bk-1 room=WEH-5409 start=540 end=600
[ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.029 s <<< FAILURE! -- in edu.cmu.cs214.lab10.harness.RaceTest
[ERROR] edu.cmu.cs214.lab10.harness.RaceTest.twoConcurrentBookingsBreaksTeammatePatch -- Time elapsed: 0.004 s <<< FAILURE!
org.opentest4j.AssertionFailedError: expected: <1> but was: <2>
```
- **Why this aim tests the patch rather than sidestepping it:**
Aiming at call 2 pauses Thread A directly after it passes the teammate's newly added second `hasOverlap` check and right before `insert`. This tests whether the patch actually closed the window between checking and inserting, proving that another thread can still insert a booking in that remaining window and cause a double-booking.

**Your verdict.** Accept or reject, and what your milestone-1 result does and
does not show about the patch.

- **Verdict: Reject.**
- **Analysis:** The green milestone-1 result only shows that pausing at the *first* check was caught by the second check; it does **not** prove the race condition was fixed. The teammate's patch merely narrowed the timing window between checking and inserting without making the composite operation atomic. A second check-then-act is still a check-then-act, leaving a window where two threads can still produce overlapping confirmed bookings.

---

## Milestone 3: the fix you would ship

**Which option, and what you changed.** Name the lock or the atomic operation
and the two operations it now covers. Paste the diff of your change and the
passing run of your milestone-1 test and the full suite.

- **Option chosen:** Option 1 (`synchronized` on the service's booking path).
- **Lock and operations covered:** The intrinsic lock on the `BookingService` instance (`this`). It now covers both the `store.hasOverlap(roomId, ...)` check and the `store.insert(booking)` operation as an indivisible critical section.
- **Diff:**
```diff
diff --git a/src/main/java/edu/cmu/cs214/lab10/service/BookingService.java b/src/main/java/edu/cmu/cs214/lab10/service/BookingService.java
index 514c3e1..a2eb4c7 100644
--- a/src/main/java/edu/cmu/cs214/lab10/service/BookingService.java
+++ b/src/main/java/edu/cmu/cs214/lab10/service/BookingService.java
@@ -62,3 +62,3 @@ public final class BookingService {
-    public BookingResult book(String roomId, int startMinute, int endMinute) {
+    public synchronized BookingResult book(String roomId, int startMinute, int endMinute) {
```
- **Passing run of milestone-1 test (`mvn test -Dtest=RaceTest`):**
```
2026-11-06T00:42:52.935Z INFO booking.confirmed booking=bk-1 room=WEH-5409 start=540 end=600
2026-11-06T00:42:52.941Z INFO booking.rejected room=WEH-5409 start=540 end=600 reason=slot already held
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.036 s -- in edu.cmu.cs214.lab10.harness.RaceTest
[INFO] BUILD SUCCESS
```
- **Passing run of full suite (`mvn -B test`):**
```
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Is your milestone-1 test still testing anything?** After your fix, describe
what that test now exercises. Did `PausingStore` pause a call during the green run? How do
you know?

- **What the test exercises:** It now verifies that concurrent callers to `book` are mutually excluded. Thread A enters `book`, acquires the `BookingService` monitor lock, and calls `store.hasOverlap`. When Thread B attempts to book concurrently, it cannot interleave; it is blocked waiting for the `BookingService` monitor lock.
- **Did `PausingStore` pause during the green run?** Yes.
- **How we know:** `gated.awaitPaused()` returned `true` (and `gated.fired()` is true). Thread A reached call 1 of `hasOverlap` and paused inside `PausingStore` while holding the service lock. Thread B was kept waiting on the monitor lock outside `book` until `gated.release()` allowed Thread A to finish and release the lock.

**What runs one at a time now that ran in parallel before.** Name concretely which callers
queue behind which. "Nothing, it is just correct now" does not count as an
answer.

- Every concurrent caller invoking `BookingService.book(...)` on the same service instance now runs one at a time. Concretely, a caller booking room `GHC-4301` from 12:00 to 13:00 will now queue behind a caller booking room `WEH-5409` from 09:00 to 10:00, even though their rooms and time slots are completely independent.

**What you left unfixed.** For anything your review flagged that you chose not
to fix, one line each on why.

- `BookingStats` counters (`confirmed`, `waitlisted`, `rejected`, `cancelled`) use unsynchronized `long` fields: left unfixed because they are explicitly documented as approximate for ops dashboards, never affect business decisions or room reservations, and avoiding lock contention on stats is an intentional design choice.
