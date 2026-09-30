import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("deploy_prism", Path(__file__).with_name("deploy-prism.py"))
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


class DeployTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.mods = root / "mods"
        self.mods.mkdir()
        self.backups = root / "mods-backup"
        self.jar = root / deploy.PACK_NAME
        self.jar.write_bytes(b"pack")
        for name in ("mod-stella-mobs-0.1.0.jar", "mc-stella-villages-0.1.0.jar", "fabric-api.jar"):
            (self.mods / name).write_bytes(b"old")
        (self.mods / "mod-stella-mobs-0.1.0.jar.pre-ring-20260930").write_bytes(b"keep")

    def test_only_the_pack_remains_and_old_jars_are_backed_up(self):
        deploy.deploy(self.jar, self.mods, self.backups)
        names = sorted(path.name for path in self.mods.iterdir())
        self.assertEqual(
            names,
            ["fabric-api.jar", deploy.PACK_NAME, "mod-stella-mobs-0.1.0.jar.pre-ring-20260930"],
        )
        backup = next(self.backups.iterdir())
        self.assertEqual(
            sorted(path.name for path in backup.iterdir()),
            ["mc-stella-villages-0.1.0.jar", "mod-stella-mobs-0.1.0.jar"],
        )
        self.assertEqual((self.mods / deploy.PACK_NAME).read_bytes(), b"pack")

    def test_dry_run_changes_nothing(self):
        old = deploy.deploy(self.jar, self.mods, self.backups, dry_run=True)
        self.assertEqual(len(old), 2)
        self.assertFalse(self.backups.exists())
        self.assertFalse((self.mods / deploy.PACK_NAME).exists())

    def test_rejects_a_jar_that_is_not_the_pack(self):
        other = self.jar.with_name("mod-stella-mobs-0.1.0.jar")
        other.write_bytes(b"x")
        with self.assertRaises(RuntimeError):
            deploy.deploy(other, self.mods, self.backups)


if __name__ == "__main__":
    unittest.main()
