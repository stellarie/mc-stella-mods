"""Starts K gym servers, each with its own port and run directory, and stops them on exit.

The launcher asks Gradle once for `build/gym-launch.json` (Java, JVM arguments, main class), then starts every server
as a plain Java process. Six `gradlew runGym` calls at once would fight over one build directory.
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import time
from pathlib import Path

from .runlog import MOD_DIR, RunLog, timestamp

BRIDGE_PORT_BASE = 25600
GAME_PORT_BASE = 25700
LISTEN_MARK = "gymBridge LISTEN"
SERVER_PROPERTIES = """\
level-type=minecraft:flat
generator-settings={"biome":"minecraft:the_void","features":false,"lakes":false,"layers":[{"block":"minecraft:air","height":1}],"structure_overrides":[]}
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
generate-structures=false
difficulty=hard
gamemode=survival
view-distance=2
simulation-distance=2
online-mode=false
enforce-secure-profile=false
max-tick-time=-1
pause-when-empty-seconds=-1
spawn-protection=0
enable-status=false
enable-query=false
enable-rcon=false
max-players=100
sync-chunk-writes=false
motd=stella gym
server-port={game_port}
"""


def gradlew() -> list[str]:
    return [str(MOD_DIR / "gradlew.bat")] if sys.platform == "win32" else ["sh", str(MOD_DIR / "gradlew")]


def prepare_spec(log: RunLog) -> dict:
    """Runs `gymLaunchSpec`, which also builds the gym classes and the modpack jars, and reads its output."""
    log.info("gymLaunch PREPARE gradle task gymLaunchSpec")
    started = time.monotonic()
    result = subprocess.run([*gradlew(), "gymLaunchSpec", "--console=plain", "-q"], cwd=MOD_DIR, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"gymLaunchSpec failed:\n{result.stdout[-2000:]}\n{result.stderr[-2000:]}")
    log.info("gymLaunch PREPARE done seconds=%.1f", time.monotonic() - started)
    return json.loads((MOD_DIR / "build" / "gym-launch.json").read_text(encoding="utf-8"))


class GymServer:
    def __init__(self, index: int, spec: dict, arenas: int, log: RunLog, sprint: bool = True, seed: int = 1,
                 decision_ticks: int = 2, extra_props: dict | None = None, heap: str = "2g", episode_ticks: int = 1200,
                 port_base: int = BRIDGE_PORT_BASE, game_port_base: int = GAME_PORT_BASE, run_prefix: str = "run-gym"):
        self.index = index
        self.spec = spec
        self.arenas = arenas
        self.log = log
        self.sprint = sprint
        self.seed = seed
        self.decision_ticks = decision_ticks
        self.episode_ticks = episode_ticks
        self.extra_props = extra_props or {}
        self.heap = heap
        self.port = port_base + index
        self.game_port = game_port_base + index
        self.run_dir = MOD_DIR / f"{run_prefix}-{index}"
        self.process: subprocess.Popen | None = None
        self.console = None

    @property
    def latest_log(self) -> Path:
        return self.run_dir / "logs" / "latest.log"

    def _prepare_run_dir(self) -> None:
        # A fresh world each launch keeps a run independent of the last one.
        # A stale latest.log would show the previous run's listen line and fool the readiness check.
        for stale in ("world", "logs"):
            if (self.run_dir / stale).exists():
                shutil.rmtree(self.run_dir / stale)
        (self.run_dir / "logs").mkdir(parents=True)
        (self.run_dir / "server.properties").write_text(SERVER_PROPERTIES.replace("{game_port}", str(self.game_port)), encoding="utf-8")
        (self.run_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
        # A trained fight writes hundreds of thousands of learn-mod lines a server-minute; the gym lines stay on regardless.
        config = self.run_dir / "config" / "mod-stella-learn"
        config.mkdir(parents=True, exist_ok=True)
        (config / "learn.json").write_text(json.dumps({"behavior_log": False, "model_archer": True}), encoding="utf-8")

    def start(self) -> None:
        self._prepare_run_dir()
        props = {
            "stella.gym.port": self.port,
            "stella.gym.arenas": self.arenas,
            "stella.gym.sprint": str(self.sprint).lower(),
            "stella.gym.decisionTicks": self.decision_ticks,
            "stella.gym.episodeTicks": self.episode_ticks,
            "stella.gym.seed": self.seed + self.index,
            **self.extra_props,
        }
        command = [self.spec["java"], f"-Xms512m", f"-Xmx{self.heap}", *self.spec["jvmArgs"],
                   *[f"-D{key}={value}" for key, value in props.items()], self.spec["mainClass"], "nogui"]
        self.console = open(self.run_dir / "logs" / "console.log", "wb")
        self.process = subprocess.Popen(command, cwd=self.run_dir, stdin=subprocess.PIPE, stdout=self.console,
                                        stderr=subprocess.STDOUT)
        self.log.info("gymLaunch START server=%d pid=%d bridgePort=%d gamePort=%d arenas=%d dir=%s", self.index,
                      self.process.pid, self.port, self.game_port, self.arenas, self.run_dir)
        self.log.event("gymLaunch START", server=self.index, pid=self.process.pid, port=self.port, arenas=self.arenas)

    def wait_ready(self, timeout: float = 300.0) -> None:
        """Waits for the bridge to log that it listens."""
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise RuntimeError(f"gym server {self.index} exited with {self.process.returncode}; see {self.run_dir / 'logs'}")
            if self.latest_log.exists() and LISTEN_MARK in self.latest_log.read_text(encoding="utf-8", errors="replace"):
                self.log.info("gymLaunch READY server=%d seconds=%.1f", self.index, timeout - (deadline - time.monotonic()))
                self.log.event("gymLaunch READY", server=self.index)
                return
            time.sleep(1.0)
        raise TimeoutError(f"gym server {self.index} did not listen within {timeout:.0f} seconds")

    def stop(self) -> None:
        if self.process is None:
            return
        if self.process.poll() is None:
            try:
                self.process.stdin.write(b"stop\n")
                self.process.stdin.flush()
                self.process.wait(timeout=30)
            except (OSError, subprocess.TimeoutExpired):
                self.process.kill()
                self.process.wait()
        self.log.info("gymLaunch STOP server=%d exit=%s", self.index, self.process.returncode)
        self.log.event("gymLaunch STOP", server=self.index, exit=self.process.returncode)
        if self.console:
            self.console.close()
        self.process = None


class GymCluster:
    """K gym servers started together. Use as a context manager so the servers stop on any exit."""

    def __init__(self, servers: int, arenas: int, log: RunLog, spec: dict | None = None, **server_options):
        self.log = log
        self.spec = spec or prepare_spec(log)
        self.servers = [GymServer(i, self.spec, arenas, log, **server_options) for i in range(servers)]

    def __enter__(self) -> "GymCluster":
        try:
            for server in self.servers:
                server.start()
            for server in self.servers:
                server.wait_ready()
        except BaseException:
            self.stop()
            raise
        return self

    def __exit__(self, *exc) -> None:
        self.stop()

    def stop(self) -> None:
        for server in self.servers:
            server.stop()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--servers", type=int, default=1)
    parser.add_argument("--arenas", type=int, default=32)
    args = parser.parse_args()
    log = RunLog(f"launch-{timestamp()}")
    with GymCluster(args.servers, args.arenas, log) as cluster:
        log.info("gymLaunch UP servers=%d ports=%s; press Ctrl+C to stop", args.servers, [s.port for s in cluster.servers])
        try:
            while all(s.process.poll() is None for s in cluster.servers):
                time.sleep(1.0)
        except KeyboardInterrupt:
            pass
    log.close()


if __name__ == "__main__":
    main()
