import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch


spec = importlib.util.spec_from_file_location(
    "build_release", Path(__file__).with_name("build-release.py")
)
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class BundleTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in ("mod-one", "mod-two"):
            mod = self.root / name
            mod.mkdir()
            for file in ("build.gradle", "settings.gradle", "gradlew", "gradlew.bat"):
                (mod / file).touch()

    def fake_run(self, command, cwd, check):
        mod = Path(cwd)
        if "build" in command:
            libs = mod / "build" / "libs"
            libs.mkdir(parents=True, exist_ok=True)
            (libs / f"{mod.name}.jar").write_bytes(mod.name.encode())
            (libs / f"{mod.name}-sources.jar").write_bytes(b"source")
        else:
            log = mod / "build" / "run" / "gameTest" / "logs" / "latest.log"
            log.parent.mkdir(parents=True, exist_ok=True)
            log.write_text("game tests ran")

    def test_bundle_contains_each_release_jar_after_five_runs_per_mod(self):
        with patch.object(release.subprocess, "run", side_effect=self.fake_run) as run:
            bundle = release.build(self.root, self.root / "output")
        self.assertEqual(run.call_count, 12)
        with zipfile.ZipFile(bundle) as archive:
            self.assertEqual(archive.namelist(), ["mod-one.jar", "mod-two.jar"])
        self.assertEqual(len(list((self.root / "output" / "test-logs").rglob("*.log"))), 10)

    def test_failed_game_test_keeps_log_and_blocks_bundle(self):
        output = self.root / "output"
        output.mkdir()
        (output / "mc-stella-mods.zip").write_bytes(b"old bundle")

        def fail_first_test(command, cwd, check):
            self.fake_run(command, cwd, check)
            if "runGameTest" in command:
                raise release.subprocess.CalledProcessError(1, command)

        with patch.object(release.subprocess, "run", side_effect=fail_first_test):
            with self.assertRaises(release.subprocess.CalledProcessError):
                release.build(self.root, output)
        self.assertTrue((self.root / "output" / "test-logs" / "mod-one" / "run-1.log").is_file())
        self.assertFalse((self.root / "output" / "mc-stella-mods.zip").exists())

    def test_failed_build_keeps_its_game_test_log(self):
        def fail_build(command, cwd, check):
            self.fake_run(command, cwd, check)
            if "build" in command:
                log = Path(cwd) / "build" / "run" / "gameTest" / "logs" / "latest.log"
                log.parent.mkdir(parents=True, exist_ok=True)
                log.write_text("build game test failed")
                raise release.subprocess.CalledProcessError(1, command)

        with patch.object(release.subprocess, "run", side_effect=fail_build):
            with self.assertRaises(release.subprocess.CalledProcessError):
                release.build(self.root, self.root / "output")
        self.assertEqual(
            (self.root / "output" / "test-logs" / "mod-one" / "run-0.log").read_text(),
            "build game test failed",
        )

    def test_linux_uses_shell_for_non_executable_wrappers(self):
        mod = self.root / "mod-one"
        with patch.object(release.sys, "platform", "linux"):
            with patch.object(release.subprocess, "run") as run:
                release.run(mod, "clean", "build")
        self.assertEqual(run.call_args.args[0][:2], ["sh", str(mod / "gradlew")])

    def test_single_mod_stages_jar_after_five_runs(self):
        output = self.root / "output"
        with patch.object(release.subprocess, "run", side_effect=self.fake_run) as run:
            release.build_one(self.root, output, "mod-one")
        self.assertEqual(run.call_count, 6)
        self.assertEqual(
            (output / "jars" / "mod-one" / "mod-one.jar").read_bytes(), b"mod-one"
        )
        self.assertFalse((output / "mc-stella-mods.zip").exists())

    def test_bundle_rejects_missing_mod_artifact(self):
        jars = self.root / "jars"
        (jars / "mod-one").mkdir(parents=True)
        (jars / "mod-one" / "mod-one.jar").write_bytes(b"one")
        with self.assertRaises(RuntimeError):
            release.bundle(jars, ["mod-one", "mod-two"], self.root / "output")
        self.assertFalse((self.root / "output" / "mc-stella-mods.zip").exists())

    def test_bundle_rejects_duplicate_jar_names(self):
        jars = self.root / "jars"
        for mod in ("mod-one", "mod-two"):
            (jars / mod).mkdir(parents=True)
            (jars / mod / "same.jar").write_bytes(mod.encode())
        with self.assertRaises(RuntimeError):
            release.bundle(jars, ["mod-one", "mod-two"], self.root / "output")
        self.assertFalse((self.root / "output" / "mc-stella-mods.zip").exists())


if __name__ == "__main__":
    unittest.main()
