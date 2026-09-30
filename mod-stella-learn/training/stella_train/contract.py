"""The archer contract. It reads the same JSON file as `com.stella.learn.model.Contract` and hashes the same bytes."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

CONTRACT_RELATIVE = Path("src/main/resources/policies/archer-v1.contract.json")
CONTINUOUS = ("forward", "strafe", "yaw", "pitch")
CLAMP = 4.0


def default_path() -> Path:
    return Path(__file__).resolve().parents[2] / CONTRACT_RELATIVE


@dataclass(frozen=True)
class Contract:
    path: Path
    version: int
    archetype: str
    decision_ticks: int
    names: tuple[str, ...]
    scales: tuple[float, ...]
    continuous: tuple[str, ...]
    jump_choices: int
    bow_choices: int
    hidden: tuple[int, ...]
    activation: str
    hash: bytes

    @property
    def obs_size(self) -> int:
        return len(self.names)

    @property
    def out_size(self) -> int:
        return len(self.continuous) + self.jump_choices + self.bow_choices

    @property
    def hash_hex(self) -> str:
        return self.hash.hex()

    def index_of(self, name: str) -> int:
        return self.names.index(name)


def load(path: Path | str | None = None) -> Contract:
    file = Path(path) if path is not None else default_path()
    raw = file.read_bytes()
    doc = json.loads(raw.decode("utf-8"))
    names = tuple(field["name"] for field in doc["observation"])
    scales = tuple(float(field["scale"]) for field in doc["observation"])
    if len(set(names)) != len(names):
        raise ValueError("contract observation names must be unique")
    if not all(scale > 0.0 for scale in scales):
        raise ValueError("contract scales must be positive")
    continuous = tuple(doc["action"]["continuous"])
    if continuous != CONTINUOUS:
        raise ValueError(f"contract continuous outputs must be {CONTINUOUS}, got {continuous}")
    discrete = doc["action"]["discrete"]
    network = doc["network"]
    if network["activation"] != "tanh":
        raise ValueError("only tanh is supported")
    return Contract(
        path=file,
        version=int(doc["version"]),
        archetype=str(doc["archetype"]),
        decision_ticks=int(doc["decision_ticks"]),
        names=names,
        scales=scales,
        continuous=continuous,
        jump_choices=int(discrete["jump"]),
        bow_choices=int(discrete["bow"]),
        hidden=tuple(int(size) for size in network["hidden"]),
        activation=network["activation"],
        hash=hashlib.sha256(raw).digest(),
    )
