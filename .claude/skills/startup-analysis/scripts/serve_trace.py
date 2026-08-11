"""Serves a directory over HTTP with the CORS header ui.perfetto.dev needs to fetch a local
trace via its ?url= deep link (plain `python3 -m http.server` lacks the header, so the fetch
hangs in a private-network preflight).

Usage:
    python3 serve_trace.py <directory-to-serve> [port]

Then open: https://ui.perfetto.dev/#!/?url=http://127.0.0.1:<port>/<trace-filename>
The first fetch can take ~30s. Stop the server once the trace shows a local_cache_key URL —
after that, perfetto serves it from the browser's cache.
"""
import http.server
import os
import sys


class CorsHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        super().end_headers()


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("usage: serve_trace.py <directory-to-serve> [port]")
    os.chdir(sys.argv[1])
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 9001
    http.server.ThreadingHTTPServer(("127.0.0.1", port), CorsHandler).serve_forever()
