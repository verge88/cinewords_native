import tempfile
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from import_flutter_api_keys import extract_dart_key, import_keys, KEY_SOURCES


class FlutterApiKeyImportTests(unittest.TestCase):
    def test_literal_and_from_environment_defaults(self):
        self.assertEqual(
            "AIzaTestToken123456789abcdef",
            extract_dart_key(
                "static const String apiKey = 'AIzaTestToken123456789abcdef';",
                "apiKey",
            ),
        )
        self.assertEqual(
            "CorrectDefaultTokenLength12345",
            extract_dart_key(
                "static const String _apiKey = String.fromEnvironment("
                "'KINOPOISK_API_KEY', defaultValue: 'CorrectDefaultTokenLength12345');",
                "_apiKey",
            ),
        )


    def test_commented_keys_are_ignored(self):
        content = (
            "// static const String apiKey = 'WrongOlderCredential123';\n"
            "static const String apiKey = String.fromEnvironment("
            "'UNUSUAL_LEGACY_ENV_NAME', defaultValue: 'CurrentToken12345678');"
        )
        self.assertEqual("CurrentToken12345678", extract_dart_key(content, "apiKey"))

    def test_import_does_not_overwrite_existing_config_without_force(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            flutter = root / "flutter"
            for name, (path, field) in KEY_SOURCES.items():
                dest = flutter / path
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_text(
                    "static const String " + field + " = 'TestValidCredential123456';",
                    encoding="utf-8",
                )
            output = root / "local.properties"
            output.write_text(
                "sdk.dir=/some/android/sdk\nYOUTUBE_API_KEY=KeepExistingToken1234\n",
                encoding="utf-8",
            )
            import_keys(flutter, output)
            content = output.read_text(encoding="utf-8")
            self.assertIn("sdk.dir=/some/android/sdk", content)
            self.assertIn("YOUTUBE_API_KEY=KeepExistingToken1234", content)
            self.assertIn("KINOPOISK_API_KEY=TestValidCredential123456", content)
            self.assertIn("OPENSUBTITLES_API_KEY=TestValidCredential123456", content)
            import_keys(flutter, output, force=True)
            self.assertIn("YOUTUBE_API_KEY=TestValidCredential123456",
                          output.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
