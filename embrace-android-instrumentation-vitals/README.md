# embrace-android-instrumentation-vitals

Captures mobile vitals: a non-configurable instrumentation that customers are opted into
automatically. The module owns the shared OS plumbing — per-frame metrics, touch interaction
listeners, and a Choreographer vsync source — and feeds it to the smoothness vital.

- **Smoothness** — framerate quality during a user interaction. A *focal moment* opens on a touch and
  concludes once the interaction goes quiet (the screen settles); the frames rendered during it are
  correlated with the interaction to gauge whether the user experienced dropped frames. Loosely
  inspired by the web's INP metric, but the signal of interest is framerate quality during the
  interaction rather than time-to-paint. See `SMOOTHNESS.md`.

Requires API 24+ (`Window.addOnFrameMetricsAvailableListener`); registers nothing on older devices.
