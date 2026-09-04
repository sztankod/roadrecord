# Route ordering fix (1.41)

The optimizer previously received absolute daily `lockedPosition` values after completed,
inactive, start and end stops had been removed from its input. Out-of-range locks were
silently skipped during route construction. The same IDs remained locked during swaps,
and saving the partial result overwrote both the order and the lock with local indices.

`PlanOrdering.locks` now projects the canonical daily order onto the exact optimizer input.
Both road-time optimization and the fallback receive the projected locks. The same mapping
is used by route movement controls. Locking a stop stores its full-day position.

Partial-route saves preserve omitted rows and locked slots; they write a unique full-day
order using an atomic DAO transaction. Only ordering columns are updated, so concurrent
GPS completion and recognition data are not overwritten by stale plan objects.

Persisted START/END anchors are applied when observing plans and recommending the next
stop, not just when adding places. This also corrects an old misordering where an END
anchor is still known. A previously overwritten manual lock with no saved END anchor
cannot be reconstructed safely: its intended position must be set again by the user.

Regression tests exercise the actual optimizer and fallback with a deliberately cheapest
last locked stop, every completion count, repeated saves, partial routes, fixed slots,
explicit unlock, multiple anchors and stale/duplicate requests. No device database was
available to reproduce the user's exact historical day. This change does not alter the
separate manually selected Waze target override.

Verification on 2026-09-04: `:app:assembleDebug :app:testDebugUnitTest` passed.
All 33 JVM tests passed, including nine new route-ordering regressions. Generated Room
code was checked to confirm the ordering batch runs inside `withTransaction`.
