"""Shared logging set-up: stdout, `log.txt`, and `events.jsonl` in one run directory."""

from __future__ import annotations

import json
import logging
import sys
import threading
import time
from datetime import datetime, timezone
from pathlib import Path

TRAINING_DIR = Path(__file__).resolve().parents[1]
RUNS_DIR = TRAINING_DIR / "runs"
MOD_DIR = TRAINING_DIR.parent


class RunLog:
    """A run directory with a text log and a JSON-lines event stream."""

    def __init__(self, name: str, base: Path = RUNS_DIR):
        self.name = name
        self.dir = base / name
        self.dir.mkdir(parents=True, exist_ok=True)
        self.logger = logging.getLogger(f"stella_train.{name}")
        self.logger.setLevel(logging.INFO)
        self.logger.propagate = False
        formatter = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
        self._handlers = [logging.StreamHandler(sys.stdout), logging.FileHandler(self.dir / "log.txt", encoding="utf-8")]
        for handler in self._handlers:
            handler.setFormatter(formatter)
            self.logger.addHandler(handler)
        self._events = open(self.dir / "events.jsonl", "a", encoding="utf-8")
        self._lock = threading.Lock()

    def info(self, message: str, *args) -> None:
        self.logger.info(message, *args)

    def warning(self, message: str, *args) -> None:
        self.logger.warning(message, *args)

    def event(self, kind: str, **fields) -> None:
        """Writes one JSON object per line; `kind` names the component and verb, as `gymBridge CONNECT`."""
        record = {"t": datetime.now(timezone.utc).isoformat(timespec="milliseconds"), "kind": kind, **fields}
        with self._lock:
            self._events.write(json.dumps(record, default=float) + "\n")
            self._events.flush()

    def close(self) -> None:
        with self._lock:
            self._events.close()
        for handler in self._handlers:
            self.logger.removeHandler(handler)
            handler.close()


def timestamp() -> str:
    return time.strftime("%Y%m%d-%H%M%S")
