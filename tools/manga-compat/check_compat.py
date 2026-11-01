#!/usr/bin/env python3
"""Check that real Mihon/Keiyoushi extension APKs can link against a KodaStream build.

The script reads the extension store index (gzip protobuf or JSON), downloads a
deterministic sample of extension APKs, extracts every member they reference in the
Mihon/library namespaces, and compares that with the classes and methods defined in the
app APK.

Exit code is non-zero only when members in `eu.kanade.tachiyomi.` or
`uy.kohesive.injekt.` are missing: those namespaces are ours to fix. Gaps in third-party
libraries are reported as warnings.
"""

from __future__ import annotations

import argparse
import gzip
import json
import random
import re
import sys
import urllib.parse
import urllib.request
from collections import defaultdict
from pathlib import Path

TRACKED_NAMESPACES = (
    "eu.kanade.tachiyomi.",
    "uy.kohesive.injekt.",
    "okhttp3.",
    "okio.",
    "rx.",
    "org.jsoup.",
    "kotlinx.serialization.",
    "kotlinx.coroutines.",
    "androidx.preference.",
    "app.cash.quickjs.",
)

CORE_NAMESPACES = ("eu.kanade.tachiyomi.", "uy.kohesive.injekt.")

REQUIRED_PACKAGES = (
    "eu.kanade.tachiyomi.extension.all.mangadex",
    "eu.kanade.tachiyomi.extension.all.mangaplus",
)

ARABIC_HINTS = (
    "eu.kanade.tachiyomi.extension.ar.teamx",
    "eu.kanade.tachiyomi.extension.ar.mangaswat",
    "eu.kanade.tachiyomi.extension.ar.azora",
    "eu.kanade.tachiyomi.extension.ar.mangalek",
    "eu.kanade.tachiyomi.extension.ar.dilar",
)

TYPE_RE = re.compile(r"L[^;\s,()]+;")
MEMBER_RE = re.compile(r"(L[^;\s,()]+;)->([^(\s,]+)(\([^)]*\)[^\s,]+)?")
UA = "KodaStream-manga-compat/1.0"


# --------------------------------------------------------------------------- protobuf

def _read_varint(data: bytes, i: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while i < len(data):
        b = data[i]
        i += 1
        value |= (b & 0x7F) << shift
        if not b & 0x80:
            return value, i
        shift += 7
        if shift > 63:
            break
    raise ValueError("bad varint")


def _parse_fields(data: bytes) -> dict[int, list[object]]:
    fields: dict[int, list[object]] = defaultdict(list)
    i = 0
    while i < len(data):
        key, i = _read_varint(data, i)
        number, wire = key >> 3, key & 7
        if wire == 0:
            value, i = _read_varint(data, i)
        elif wire == 1:
            value, i = data[i:i + 8], i + 8
        elif wire == 2:
            length, i = _read_varint(data, i)
            value, i = data[i:i + length], i + length
        elif wire == 5:
            value, i = data[i:i + 4], i + 4
        else:
            raise ValueError(f"unsupported wire type {wire}")
        fields[number].append(value)
    return fields


def _text(value: object) -> str:
    return value.decode("utf-8", "replace") if isinstance(value, bytes) else str(value)


def _message(value: object) -> dict[int, list[object]]:
    return _parse_fields(value) if isinstance(value, bytes) else {}


def parse_store_index(raw: bytes) -> tuple[list[dict], str | None]:
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    stripped = raw.lstrip()
    if stripped[:1] == b"[" or stripped[:1] == b"{":
        return _parse_json_index(stripped), None
    return _parse_proto_index(raw), None


def _parse_proto_index(raw: bytes) -> list[dict]:
    store = _parse_fields(raw)
    extensions: list[dict] = []
    for list_msg in store.get(101, []):
        message = _message(list_msg)
        for ext_msg in message.get(1, []):
            extensions.append(_parse_proto_extension(_message(ext_msg)))
    # Some stores inline the repeated extensions at 101 instead of wrapping them.
    if not extensions:
        for ext_msg in store.get(101, []):
            extensions.append(_parse_proto_extension(_message(ext_msg)))
    return [e for e in extensions if e.get("package")]


def _parse_proto_extension(fields: dict[int, list[object]]) -> dict:
    resources = _message(fields.get(3, [b""])[0]) if fields.get(3) else {}
    return {
        "name": _text(fields.get(1, [b""])[0]),
        "package": _text(fields.get(2, [b""])[0]),
        "apk": _text(resources.get(1, [b""])[0]),
        "icon": _text(resources.get(2, [b""])[0]),
        "lib": _text(fields.get(4, [b""])[0]),
        "versionCode": int(fields.get(5, [0])[0]),
        "versionName": _text(fields.get(6, [b""])[0]),
        "warning": int(fields.get(7, [0])[0]),
        "sources": [
            {
                "id": int(_message(s).get(1, [0])[0]),
                "name": _text(_message(s).get(2, [b""])[0]),
                "lang": _text(_message(s).get(3, [b""])[0]),
            }
            for s in fields.get(8, [])
        ],
    }


def _parse_json_index(raw: bytes) -> list[dict]:
    data = json.loads(raw.decode("utf-8", "replace"))
    if isinstance(data, dict):
        data = data.get("extensions") or data.get("extensionList") or []
    result = []
    for item in data:
        pkg = item.get("pkg") or item.get("packageName") or item.get("package")
        if not pkg:
            continue
        result.append({
            "name": item.get("name", ""),
            "package": pkg,
            "apk": item.get("apk") or item.get("apkUrl") or "",
            "icon": item.get("icon") or "",
            "lib": str(item.get("extensionLib") or item.get("lib") or ""),
            "versionCode": int(item.get("versionCode") or 0),
            "versionName": item.get("versionName", ""),
            "warning": int(item.get("contentWarning") or 0),
            "sources": [],
        })
    return result


# --------------------------------------------------------------------------- sample

def choose_sample(extensions: list[dict], count: int) -> list[dict]:
    by_pkg = {e["package"]: e for e in extensions}
    chosen: list[dict] = []

    def add(pkg: str) -> None:
        item = by_pkg.get(pkg)
        if item and item not in chosen:
            chosen.append(item)

    for pkg in REQUIRED_PACKAGES:
        add(pkg)

    arabic = sorted(
        (e for e in extensions if ".extension.ar." in e["package"]),
        key=lambda e: e["package"],
    )
    preferred = [p for p in ARABIC_HINTS if p in by_pkg]
    for pkg in preferred:
        add(pkg)
    for item in arabic:
        if sum(".extension.ar." in e["package"] for e in chosen) >= 10:
            break
        add(item["package"])

    rng = random.Random(0)
    rest = [e for e in extensions if e not in chosen]
    rng.shuffle(rest)
    for item in rest:
        if len(chosen) >= count:
            break
        add(item["package"])

    return chosen[:count]


# --------------------------------------------------------------------------- dex

def _type_prefix(class_name: str) -> str:
    return class_name[1:].replace("/", ".")


def _tracked(class_name: str) -> bool:
    if not class_name.startswith("L"):
        return False
    dotted = _type_prefix(class_name)
    return any(dotted.startswith(ns) for ns in TRACKED_NAMESPACES)


def _ns_of(class_name: str) -> str:
    dotted = _type_prefix(class_name)
    for ns in TRACKED_NAMESPACES:
        if dotted.startswith(ns):
            return ns
    return "other"


def collect_app_symbols(
    apk: Path,
) -> tuple[set[str], dict[str, set[tuple[str, str]]], dict[str, str], dict[str, list[str]]]:
    from androguard.misc import AnalyzeAPK

    _, dexes, _ = AnalyzeAPK(str(apk))
    classes: set[str] = set()
    methods: dict[str, set[tuple[str, str]]] = defaultdict(set)
    superclass: dict[str, str] = {}
    interfaces: dict[str, list[str]] = {}
    for dex in dexes:
        for cls in dex.get_classes():
            name = cls.get_name()
            classes.add(name)
            sup = cls.get_superclassname()
            if sup:
                superclass[name] = sup
            iface = [i for i in cls.get_interfaces() if i]
            if iface:
                interfaces[name] = iface
            for method in cls.get_methods():
                methods[name].add((method.get_name(), method.get_descriptor()))
    return classes, methods, superclass, interfaces


def _own_classes(dex) -> set[str]:
    return {cls.get_name() for cls in dex.get_classes()}


def collect_refs(apk: Path) -> tuple[set[str], set[tuple[str, str, str | None]], set[tuple[str, str, str]]]:
    """Return referenced classes, methods and fields from one extension APK."""
    from androguard.misc import AnalyzeAPK

    _, dexes, _ = AnalyzeAPK(str(apk))
    ref_classes: set[str] = set()
    ref_methods: set[tuple[str, str, str | None]] = set()
    ref_fields: set[tuple[str, str, str]] = set()

    def scan(text: str) -> None:
        for match in MEMBER_RE.finditer(text):
            owner, name, descriptor = match.groups()
            if descriptor:
                ref_methods.add((owner, name, descriptor))
            else:
                ref_fields.add((owner, name, ""))
        ref_classes.update(TYPE_RE.findall(text))

    for dex in dexes:
        for cls in dex.get_classes():
            scan(cls.get_superclassname() or "")
            for interface in cls.get_interfaces():
                scan(interface)
            for field in cls.get_fields():
                scan(field.get_descriptor())
            for method in cls.get_methods():
                scan(method.get_descriptor())
                for ins in method.get_instructions():
                    try:
                        scan(ins.get_output())
                    except Exception:
                        continue
    return ref_classes, ref_methods, ref_fields


def method_exists(
    owner: str,
    name: str,
    descriptor: str | None,
    classes: set[str],
    methods: dict[str, set[tuple[str, str]]],
    superclass: dict[str, str],
    interfaces: dict[str, list[str]],
) -> bool:
    """Search the owner, its superclasses and their interfaces (default methods)."""
    seen = set()
    pending = [owner]
    while pending:
        current = pending.pop()
        if not current or current in seen:
            continue
        seen.add(current)
        if current in classes and (name, descriptor or "") in methods.get(current, set()):
            return True
        sup = superclass.get(current)
        if sup:
            pending.append(sup)
        pending.extend(interfaces.get(current, []))
    return False


# --------------------------------------------------------------------------- main

def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--store-url", required=True)
    parser.add_argument("--samples", type=int, default=40)
    parser.add_argument("--out-dir", type=Path, default=Path("."))
    parser.add_argument("--cache-dir", type=Path, default=Path("manga-ext-cache"))
    args = parser.parse_args()

    args.out_dir.mkdir(parents=True, exist_ok=True)
    args.cache_dir.mkdir(parents=True, exist_ok=True)

    print(f"Reading store {args.store_url}")
    extensions, _ = parse_store_index(fetch(args.store_url))
    print(f"Store has {len(extensions)} extensions")

    sample = choose_sample(extensions, args.samples)
    print(f"Sample: {len(sample)} extensions")

    app_classes, app_methods, app_superclass, app_interfaces = collect_app_symbols(args.apk)
    print(f"App defines {len(app_classes)} classes")

    report: dict = {
        "storeUrl": args.store_url,
        "sampleRequested": args.samples,
        "sampleSize": len(sample),
        "storeSize": len(extensions),
        "extensions": [],
        "namespaces": defaultdict(lambda: {"missing": defaultdict(list)}),
    }

    fully_linkable = 0
    core_missing_total = 0

    for index, extension in enumerate(sample, 1):
        pkg = extension["package"]
        print(f"[{index}/{len(sample)}] {pkg}")
        entry = {"package": pkg, "name": extension.get("name", ""), "missing": []}
        apk_url = extension.get("apk") or ""
        if not apk_url:
            entry["error"] = "no apk url"
            report["extensions"].append(entry)
            continue
        apk_url = urllib.parse.urljoin(args.store_url, apk_url)
        local = args.cache_dir / f"{pkg}.apk"
        try:
            if not local.is_file():
                local.write_bytes(fetch(apk_url))
            ref_classes, ref_methods, ref_fields = collect_refs(local)
            own = set()
            from androguard.misc import AnalyzeAPK
            _, dexes, _ = AnalyzeAPK(str(local))
            for dex in dexes:
                own.update(_own_classes(dex))
        except Exception as e:  # noqa: BLE001 - report per extension, don't stop the run
            entry["error"] = f"{type(e).__name__}: {e}"
            report["extensions"].append(entry)
            continue

        missing = []
        for cls in sorted(ref_classes):
            if cls in own or not _tracked(cls):
                continue
            if cls not in app_classes:
                missing.append({"kind": "class", "owner": cls, "name": "", "descriptor": ""})
        for owner, name, descriptor in sorted(ref_methods):
            if owner in own or not _tracked(owner):
                continue
            if not method_exists(
                owner, name, descriptor,
                app_classes, app_methods, app_superclass, app_interfaces,
            ):
                missing.append({"kind": "method", "owner": owner, "name": name, "descriptor": descriptor})
        for owner, name, _ in sorted(ref_fields):
            if owner in own or not _tracked(owner):
                continue
            if owner not in app_classes:
                missing.append({"kind": "field", "owner": owner, "name": name, "descriptor": ""})

        # Remove duplicates while keeping determinism.
        deduped = []
        seen = set()
        for item in missing:
            key = (item["kind"], item["owner"], item["name"], item["descriptor"])
            if key in seen:
                continue
            seen.add(key)
            deduped.append(item)
        entry["missing"] = deduped
        if deduped:
            for item in deduped:
                ns = _ns_of(item["owner"])
                report["namespaces"][ns]["missing"][
                    f'{item["kind"]} {item["owner"]}->{item["name"]}{item["descriptor"]}'
                ].append(pkg)
            core_missing_total += sum(1 for i in deduped if _ns_of(i["owner"]) in CORE_NAMESPACES)
        else:
            fully_linkable += 1
        report["extensions"].append(entry)

    report["fullyLinkable"] = fully_linkable
    report["fullyLinkablePercent"] = round(100.0 * fully_linkable / max(1, len(sample)), 1)
    report["coreMissing"] = core_missing_total
    report["namespaces"] = {
        ns: {"missing": dict(sorted(value["missing"].items()))}
        for ns, value in sorted(report["namespaces"].items())
    }

    json_path = args.out_dir / "compat-report.json"
    json_path.write_text(json.dumps(report, indent=2))

    lines = [
        "# Manga extension compatibility",
        "",
        f"- Store: `{args.store_url}`",
        f"- Store extensions: **{len(extensions)}**",
        f"- Sample: **{len(sample)}**",
        f"- Fully linkable: **{fully_linkable}/{len(sample)}** ({report['fullyLinkablePercent']}%)",
        f"- Missing Mihon core members: **{core_missing_total}**",
        "",
    ]
    if report["namespaces"]:
        lines.append("## Missing members by namespace")
        lines.append("")
        for ns, value in report["namespaces"].items():
            total = len(value["missing"])
            lines.append(f"### `{ns}` — {total} missing")
            lines.append("")
            for member, packages in list(value["missing"].items())[:40]:
                packages_text = ", ".join(sorted(set(packages))[:5])
                lines.append(f"- `{member}` — {packages_text}")
            if total > 40:
                lines.append(f"- … and {total - 40} more (see JSON)")
            lines.append("")
    else:
        lines.append("No missing members in tracked namespaces.")
    md_path = args.out_dir / "compat-report.md"
    md_path.write_text("\n".join(lines) + "\n")
    print("\n".join(lines))

    summary = Path(sys.argv[0]).name
    step_summary = Path("/tmp/compat-step-summary.md")
    step_summary.write_text("\n".join(lines) + "\n")
    print(f"Wrote {json_path} and {md_path} ({summary})")
    return 1 if core_missing_total else 0


if __name__ == "__main__":
    sys.exit(main())
