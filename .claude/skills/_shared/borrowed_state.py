#!/usr/bin/env python3
"""Borrow a piece of shared mutable state and give it back even if the process is killed.

Campaigns mutate things that outlive them - above all the ExampleApp gradle catalog's SDK version
pin, which a campaign flips per version leg. If the process dies holding it, the next unrelated
build silently resolves the wrong SDK, and nothing about the failure points at the cause.

`try/finally` alone is NOT enough, and the failure is common rather than theoretical: any campaign
killed mid-run leaves the borrowed value stale, and the damage is silent until some later build
resolves the wrong thing. SIGTERM does not run `finally`, and SIGKILL does not run anything at all.

Three layers, because each covers what the others cannot:

  1. a MARKER FILE - survives SIGKILL, power loss, the machine going down. The only durable layer.
  2. SIGNAL HANDLERS - SIGTERM/SIGINT/SIGHUP, which skip `finally`.
  3. the context manager's own `finally` - normal exit and exceptions.

Ordering rule that matters more than it looks: recovery runs BEFORE the current value is read.
Otherwise a stale value left by a dead run is mistaken for the user's own setting and then
faithfully "restored" on exit, which makes the damage permanent instead of repairing it.

Usage:

    with BorrowedState(marker, read_pin, write_pin, log) as original:
        ...                       # mutate freely; it is given back no matter how this ends
"""
import os
import signal


class BorrowedState:
    """Context manager that restores a mutated value on exit, on signal, or on the next run.

    read/write are callables so this stays testable and agnostic about what is being borrowed -
    a gradle catalog pin, a device setting, a compile state.
    """

    def __init__(self, marker_path, read, write, log=print, signals=None):
        self.marker = marker_path
        self.read = read
        self.write = write
        self.log = log
        self.signals = signals or (signal.SIGTERM, signal.SIGINT, signal.SIGHUP)
        self.original = None
        self._restored = False

    def recover(self):
        """Restore a value left behind by a run that died. Call BEFORE reading the current one."""
        if not self.marker.exists():
            return
        stale = self.marker.read_text().strip()
        self.marker.unlink(missing_ok=True)
        if stale and self.read() != stale:
            self.log(f"RECOVERY: previous run left state unrestored; setting back to {stale}")
            self.write(stale)

    def restore(self, reason):
        """Idempotent: safe to reach from a signal handler and the finally block both."""
        if self._restored or self.original is None:
            return
        self._restored = True
        self.write(self.original)
        self.log(f"restored borrowed state ({reason}): {self.read()}")
        self.marker.unlink(missing_ok=True)

    def __enter__(self):
        self.recover()
        self.original = self.read()
        self.marker.parent.mkdir(parents=True, exist_ok=True)
        self.marker.write_text(self.original or "")

        def on_signal(signum, _frame):
            self.restore(f"signal {signum}")
            # Re-raise under the default handler so the exit status still reflects the signal
            # rather than looking like a clean exit to whatever is supervising this.
            signal.signal(signum, signal.SIG_DFL)
            os.kill(os.getpid(), signum)

        for sig in self.signals:
            try:
                signal.signal(sig, on_signal)
            except (ValueError, OSError):
                # Not on the main thread, or the signal does not exist here. The marker file
                # still covers the hard cases, so degrade rather than refuse to run.
                self.log(f"note: could not install handler for signal {sig}")
        return self.original

    def __exit__(self, exc_type, exc, tb):
        self.restore("normal exit" if exc_type is None else f"exception {exc_type.__name__}")
        return False
