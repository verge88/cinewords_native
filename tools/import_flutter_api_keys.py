#!/usr/bin/env python3
"""Import client-side API configuration from the Flutter CineWords checkout.

No credentials are printed or checked into the native repository. The generated
local.properties is ignored by Git. Do NOT import Supabase service_role keys.
"""
import argparse
import os
from pathlib import Path
import re
from tempfile import TemporaryDirectory
from urllib.request import Request, urlopen

KEY_SOURCES = {
    "YOUTUBE_API_KEY": ("lib/services/youtube_config.dart", "apiKey"),
    "KINOPOISK_API_KEY": ("lib/services/kinopoisk_service.dart", "_apiKey"),
    "OPENSUBTITLES_API_KEY": ("lib/services/open_subtitles_service.dart", "apiKey"),
}
VALID_VALUE = re.compile(r"^[A-Za-z0-9_.:-]{12,}$")


def extract_dart_key(source: str, identifier: str) -> str:
    # OpenSubtitlesService contains commented-out obsolete API keys; ignore them.
    source = "\n".join(line for line in source.splitlines()
                       if not line.lstrip().startswith("//"))
    start = re.search(r"\b" + re.escape(identifier) + r"\s*=", source)
    if not start:
        raise ValueError("Flutter configuration field is missing: " + identifier)
    tail = source[start.end():]
    env_default = re.match(
        r"""\s*String\.fromEnvironment\(\s*['"][^'"]+['"]\s*,\s*defaultValue:\s*['"]([^'"]+)['"]""",
        tail, flags=re.DOTALL,
    )
    literal = re.match(r"""\s*['"]([^'"]+)['"]""", tail)
    match = env_default or literal
    if not match:
        raise ValueError("Cannot extract client-side default for: " + identifier)
    value = match.group(1)
    if not VALID_VALUE.fullmatch(value) or re.search(
        r"(?i)(placeholder|your_key|replace_me|paste_key|^example)", value
    ):
        raise ValueError("Source key appears invalid or a placeholder: " + identifier)
    return value


def import_keys(source_dir: Path, properties_file: Path, force: bool = False) -> dict:
    values = {}
    for env_name, (source_file, dart_field) in KEY_SOURCES.items():
        content = (source_dir / source_file).read_text(encoding="utf-8")
        values[env_name] = extract_dart_key(content, dart_field)

    original = properties_file.read_text(encoding="utf-8") if properties_file.exists() else ""
    lines = original.splitlines()
    for name, value in values.items():
        index = next(
            (i for i, line in enumerate(lines)
             if line.split("=", 1)[0].strip() == name and "=" in line), None
        )
        if index is None:
            lines.append(name + "=" + value)
        elif force or not lines[index].split("=", 1)[1].strip():
            lines[index] = name + "=" + value

    properties_file.parent.mkdir(parents=True, exist_ok=True)
    properties_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
    if os.name != "nt":
        properties_file.chmod(0o600)
    return values


def download_public_flutter_configs(output_dir: Path) -> None:
    """Fetch only the three public configuration files; Flutter has broken gitlinks."""
    for source_file, _ in KEY_SOURCES.values():
        url = ("https://raw.githubusercontent.com/verge88/cinewords/master/"
               + source_file)
        request = Request(url, headers={"User-Agent": "CineWords-Native-CI"})
        with urlopen(request, timeout=20) as response:
            content = response.read().decode("utf-8")
        dest = output_dir / source_file
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(content, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path("../cinewords"),
                        help="Path to an existing Flutter cinewords checkout")
    parser.add_argument("--github", action="store_true",
                        help="Fetch only the public Flutter config files from GitHub")
    parser.add_argument("--output", type=Path, default=Path("local.properties"),
                        help="Ignored Android local.properties file")
    parser.add_argument("--force", action="store_true",
                        help="Replace existing API key entries in local.properties")
    args = parser.parse_args()
    if args.github:
        with TemporaryDirectory(prefix="cinewords-flutter-config-") as temporary:
            public_source = Path(temporary)
            download_public_flutter_configs(public_source)
            imported = import_keys(public_source, args.output, force=args.force)
    else:
        imported = import_keys(args.source, args.output, force=args.force)
    print("Imported Flutter API configuration: " + ", ".join(imported.keys()))
    print("Values are stored only in " + str(args.output) + " (not printed).")


if __name__ == "__main__":
    main()
