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
        libs = mod / "build" / "libs"
        libs.mkdir(parents=True, exist_ok=True)
        (libs / f"{mod.name}.jar").write_bytes(mod.name.encode())
        (libs / f"{mod.name}-sources.jar").write_bytes(b"source")

    def test_bundle_contains_each_release_jar_and_runs_no_tests(self):
        with patch.object(release.subprocess, "run", side_effect=self.fake_run) as run:
            bundle = release.build(self.root, self.root / "output")
        self.assertEqual(run.call_count, 2)
        for call in run.call_args_list:
            self.assertIn("assemble", call.args[0])
            self.assertNotIn("build", call.args[0])
            self.assertNotIn("runGameTest", call.args[0])
        with zipfile.ZipFile(bundle) as archive:
            self.assertEqual(archive.namelist(), ["mod-one.jar", "mod-two.jar"])
        self.assertFalse((self.root / "output" / "test-logs").exists())

    def test_failed_build_blocks_bundle(self):
        output = self.root / "output"
        output.mkdir()
        (output / "mc-stella-mods.zip").write_bytes(b"old bundle")

        def fail_build(command, cwd, check):
            raise release.subprocess.CalledProcessError(1, command)

        with patch.object(release.subprocess, "run", side_effect=fail_build):
            with self.assertRaises(release.subprocess.CalledProcessError):
                release.build(self.root, output)
        self.assertFalse((output / "mc-stella-mods.zip").exists())

    def test_linux_uses_shell_for_non_executable_wrappers(self):
        mod = self.root / "mod-one"
        with patch.object(release.sys, "platform", "linux"):
            with patch.object(release.subprocess, "run") as run:
                release.run(mod, "clean", "assemble")
        self.assertEqual(run.call_args.args[0][:2], ["sh", str(mod / "gradlew")])

    def test_single_mod_stages_jar_without_bundling(self):
        output = self.root / "output"
        with patch.object(release.subprocess, "run", side_effect=self.fake_run) as run:
            release.build_one(self.root, output, "mod-one")
        self.assertEqual(run.call_count, 1)
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

    def test_nether_serializes_its_included_builds(self):
        mod = self.root / "mod-stella-nether"
        mod.mkdir()
        for file in ("build.gradle", "settings.gradle", "gradlew", "gradlew.bat"):
            (mod / file).touch()
        with patch.object(release.subprocess, "run", side_effect=self.fake_run) as run:
            release.build_one(self.root, self.root / "output", mod.name)
        self.assertEqual(run.call_count, 1)
        self.assertTrue(all("--max-workers=1" in call.args[0] for call in run.call_args_list))


class NextVersionTests(unittest.TestCase):
    def test_first_release_is_1_0_0_whatever_the_bump(self):
        for bump in ("patch", "minor", "major"):
            self.assertEqual(release.next_version([], bump), "v1.0.0")

    def test_unrelated_tags_do_not_count(self):
        self.assertEqual(release.next_version(["latest", "v1", "v1.0.0-rc1"], "patch"), "v1.0.0")

    def test_patch_bumps_the_highest_tag_not_the_last_listed(self):
        tags = ["v1.0.9", "v1.0.10", "v1.0.2"]
        self.assertEqual(release.next_version(tags, "patch"), "v1.0.11")

    def test_minor_and_major_reset_the_lower_parts(self):
        self.assertEqual(release.next_version(["v1.4.7"], "minor"), "v1.5.0")
        self.assertEqual(release.next_version(["v1.4.7"], "major"), "v2.0.0")

    def test_unknown_bump_is_rejected(self):
        with self.assertRaises(RuntimeError):
            release.next_version(["v1.0.0"], "huge")


if __name__ == "__main__":
    unittest.main()
