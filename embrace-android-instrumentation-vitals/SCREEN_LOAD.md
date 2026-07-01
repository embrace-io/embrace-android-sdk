## Screen Load Vital

A screen load measures the time from a user's initial interaction until the destination screen has
*settled* (gone quiet). Settling is the same measurement used to determine the interaction-end for the
Smoothness vital, but where smoothness measures framerate quality during an interaction, the screen load
measures how long the destination takes to become interactive.

### The event sequence

A screen load is built from four signals, driven onto the Vitals handler thread by the focal-moment
tracker:

1. **Tap** (touch up) — a committed user action that may trigger a navigation. Opens a *candidate*.
2. **Navigation start** — confirms the candidate is a real navigation.
3. **Navigation end** — the destination is reached; arms the settle.
4. **Settle** — the destination goes quiet for the idle threshold; the load ends.

The tap is the start because it precedes the navigation and captures the click handler, `startActivity`,
and the destination launching — but the press-and-hold dwell before release is user behaviour, not load
latency, so the release (not the touch-down) anchors the load. A *touchless* navigation (e.g. an Activity
transition with no preceding tap) instead anchors the load at the navigation start itself.

If either the navigation start or the navigation end is missing, the duration is not considered a screen
load. The screen name comes from the navigation start or end (last writer wins, so an immediate redirect
resolves to the final destination). The navigation signals are deliberately abstracted internal hooks so
that other instrumentations (such as `embrace-android-instrumentation-navigation`) can drive them.

### Outcomes

A load that goes quiet on its own is reported as `SETTLED`, ending at its last rendered activity. Once
navigation end has armed the settle, three other things can end it:

- **`NAVIGATION_INTERRUPTED`** — a new (touchless) navigation starts while the destination is still
  settling. The destination was shown long enough to navigate away from, so it is treated as loaded: the
  in-flight load is settled at its last rendered activity (last frame or focus gain, not the interrupting
  navigation moment), and a fresh load opens for the new destination. A rapid redirect therefore emits two
  loads — the first (`NAVIGATION_INTERRUPTED`) and the second — rather than folding into one. An
  interruption preceded by a fresh tap is a `USER_INTERRUPTED` instead, since the tap proves the screen was
  interactive.
- **`TIMED_OUT`** — a destination with continuously animating content never goes quiet, so the settle would
  never occur and the load would run forever. To bound this, a load is given up 30 seconds after it starts.
  Because the screen never actually settled, the end time is the first frame rendered *after* navigation end
  — the earliest plausible "loaded" moment — rather than the 30s mark. A timeout reached before navigation
  end is an incomplete sequence and is not reported.
- **`USER_INTERRUPTED`** — the user taps the still-settling destination. The screen was interactive enough
  to act on, so the load ends at that tap rather than waiting for a settle the user has pre-empted. (A tap
  before navigation end is not a real screen load yet, so it is discarded and starts a fresh candidate.)

### Window focus

The destination's open-transition animation renders no app frames (the system composites it), so the
frame-driven settle cannot see it. When the window gains input focus — the animation has finished and the
screen is interactive — that moment is treated as activity, extending an in-flight load to cover the
animation tail rather than ending it at the last content frame.
