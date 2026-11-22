#!/usr/bin/env python3
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import time
import zipfile

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
STORE_DIR = REPO_ROOT / "store"
CATALOG_PATH = STORE_DIR / "modules.json"
ZIPS_DIR = STORE_DIR / "zips"
ICONS_DIR = STORE_DIR / "icons"
BANNERS_DIR = STORE_DIR / "banners"
RAW_BASE = "https://raw.githubusercontent.com/Rizky088/Mochy-Music-Custom/main/store"
DOWNLOAD_DIRS = [
    pathlib.Path("/sdcard/Download"),
    pathlib.Path.home() / "storage/downloads",
]
TAG_TO_KIND = {
    "Looks": "look",
    "Sources": "source",
    "Players": "player",
    "Tools": "other",
}


def load_catalog() -> dict:
    return json.loads(CATALOG_PATH.read_text(encoding="utf-8"))


def get_catalog_categories(cat: dict | None = None) -> list[str]:
    data = cat if cat is not None else load_catalog()
    return [str(x).strip() for x in data.get("categories", []) if str(x).strip()]


def save_catalog(cat: dict) -> None:
    CATALOG_PATH.write_text(json.dumps(cat, indent=2) + "\n", encoding="utf-8")


def slugify(text: str) -> str:
    s = re.sub(r"[^a-zA-Z0-9_-]+", "-", text.strip().lower()).strip("-")
    return s[:80] or f"module-{int(time.time())}"


def prompt(label: str, default: str = "") -> str:
    suffix = f" [{default}]" if default else ""
    val = input(f"{label}{suffix}: ").strip()
    return val if val else default


def prompt_yes_no(label: str, default: bool = True) -> bool:
    hint = "Y/n" if default else "y/N"
    val = input(f"{label} ({hint}): ").strip().lower()
    if not val:
        return default
    return val in {"y", "yes"}


def list_recent_files(exts: tuple[str, ...], limit: int = 20) -> list[pathlib.Path]:
    found: dict[str, pathlib.Path] = {}
    for d in DOWNLOAD_DIRS:
        if not d.is_dir():
            continue
        for p in d.iterdir():
            if p.is_file() and p.suffix.lower() in exts:
                found[str(p.resolve())] = p
    return sorted(found.values(), key=lambda p: p.stat().st_mtime, reverse=True)[:limit]


def pick_tags(default_tags: list[str] | None = None, catalog_categories: list[str] | None = None) -> tuple[str, list[str]]:
    base_options = ["Looks", "Sources", "Players", "Tools"]
    for c in (catalog_categories or []):
        if c and c not in base_options:
            base_options.append(c)

    defaults = [t.strip() for t in (default_tags or ["Looks"]) if t and t.strip()][:2]
    if not defaults:
        defaults = ["Looks"]

    default_display = ", ".join(defaults)
    print("\nCategories / Tags (pick 1 or 2):")
    for i, opt in enumerate(base_options, 1):
        mark = " *" if opt in defaults else ""
        print(f"  {i}. {opt}{mark}")
    raw = input(f"Enter numbers or names separated by comma/space (max 2) [Enter = {default_display}]: ").strip()

    chosen: list[str] = []
    if raw:
        tokens = [tok.strip() for tok in re.split(r"[,\s]+", raw) if tok.strip()]
        for tok in tokens:
            if tok.isdigit():
                idx = int(tok) - 1
                if 0 <= idx < len(base_options):
                    cand = base_options[idx]
                else:
                    continue
            else:
                matched = next((opt for opt in base_options if opt.lower() == tok.lower()), None)
                cand = matched or tok
            if cand not in chosen:
                chosen.append(cand)
            if len(chosen) >= 2:
                break

    tags = chosen if chosen else defaults[:2]
    primary_tag = tags[0]
    kind = TAG_TO_KIND.get(primary_tag) or TAG_TO_KIND.get(primary_tag.capitalize()) or "other"
    return kind, tags


def pick_optional_image(label: str, slug: str, dest_dir: pathlib.Path, current: str = "") -> str:
    raw = prompt(f"{label} (https URL, local file path, 'clear', or Enter to keep)", current)
    if raw.lower() == "clear":
        return ""
    if not raw or raw.startswith("https://"):
        return raw
    p = pathlib.Path(raw).expanduser()
    if not p.is_file():
        alt = pathlib.Path("/sdcard/Download") / raw
        if alt.is_file():
            p = alt
    if p.is_file():
        dest_dir.mkdir(parents=True, exist_ok=True)
        ext = p.suffix.lower() or ".png"
        out = dest_dir / f"{slug}{ext}"
        shutil.copy2(p, out)
        folder = dest_dir.name
        return f"{RAW_BASE}/{folder}/{slug}{ext}"
    print(f"  ! File not found: {raw} (keeping previous value)")
    return current


def inspect_zip(src: pathlib.Path) -> tuple[dict, bytes | None, str, list[str]]:
    meta: dict = {}
    icon_bytes = None
    icon_ext = ".png"
    with zipfile.ZipFile(src, "r") as zf:
        names = zf.namelist()
        json_files = sorted(
            [n for n in names if n.lower().endswith(".json") and not n.endswith("/")],
            key=lambda n: (
                n.count("/"),
                0 if os.path.basename(n).lower() in {"manifest.json", "module.json", "info.json"} else 1,
                len(n),
            ),
        )
        for jf in json_files:
            try:
                parsed = json.loads(zf.read(jf).decode("utf-8-sig"))
                if isinstance(parsed, dict) and any(k in parsed for k in ("id", "moduleId", "name", "version")):
                    meta = parsed
                    break
            except Exception:
                continue

        icon_hint = str(meta.get("icon") or meta.get("art") or "").lstrip("./")
        icon_match = None
        if icon_hint:
            icon_match = next((n for n in names if n.endswith(icon_hint) and not n.endswith("/")), None)
        if not icon_match:
            icon_match = next(
                (n for n in names if re.search(r"(^|/)icon\.(png|jpe?g|webp)$", n, re.I)),
                None,
            )
        if icon_match:
            icon_bytes = zf.read(icon_match)
            icon_ext = pathlib.Path(icon_match).suffix.lower() or ".png"
    return meta, icon_bytes, icon_ext, names


def push_changes(message: str) -> None:
    if not prompt_yes_no("Push changes to GitHub now?", default=True):
        print("Saved locally. Run option 6 later to push.")
        return
    subprocess.run(["git", "-C", str(REPO_ROOT), "add", "store/"], check=True)
    status = subprocess.run(
        ["git", "-C", str(REPO_ROOT), "status", "--porcelain", "store/"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()
    if not status:
        print("No store changes to commit.")
        return
    subprocess.run(["git", "-C", str(REPO_ROOT), "commit", "-m", message], check=True)
    subprocess.run(["git", "-C", str(REPO_ROOT), "pull", "--rebase", "origin", "main"], check=True)
    subprocess.run(["git", "-C", str(REPO_ROOT), "push", "origin", "main"], check=True)
    print("\nPushed! Your Module Store updates in ~1-2 minutes (no APK rebuild needed).")


def add_zip_module() -> None:
    zips = list_recent_files((".zip",))
    print("\n--- Pick a .zip file ---")
    for i, z in enumerate(zips, 1):
        kb = z.stat().st_size // 1024
        print(f"  {i}. {z.name} ({kb} KB)")
    choice = input("Pick number or paste full path to .zip: ").strip()
    if not choice:
        return
    if choice.isdigit() and 1 <= int(choice) <= len(zips):
        src = zips[int(choice) - 1]
    else:
        src = pathlib.Path(choice).expanduser()
        if not src.is_file():
            alt = pathlib.Path("/sdcard/Download") / choice
            if alt.is_file():
                src = alt
    if not src.is_file():
        print(f"File not found: {src}")
        return

    meta, icon_bytes, icon_ext, names = inspect_zip(src)
    author_raw = meta.get("author")
    if isinstance(author_raw, dict):
        author_raw = author_raw.get("name") or ""

    default_name = str(meta.get("name") or meta.get("title") or src.stem).strip()
    default_module_id = str(meta.get("id") or meta.get("moduleId") or slugify(src.stem)).strip()
    default_slug = slugify(default_module_id or default_name)
    default_version = str(meta.get("version") or "1.0.0").strip()
    default_author = str(author_raw or "Rizky088").strip()
    default_desc = str(meta.get("description") or "").strip()

    blob = (json.dumps(meta) + " " + " ".join(names)).lower()
    if any(k in blob for k in ("look", "skin", "theme", "tablet", "layout")):
        default_tag = "Looks"
    elif any(k in blob for k in ("source", "audio", "stream")):
        default_tag = "Sources"
    elif "player" in blob:
        default_tag = "Players"
    else:
        default_tag = "Tools"

    print(f"\nDetected: {default_name} v{default_version} by {default_author} (id: {default_module_id})")
    name = prompt("Module name", default_name)
    slug = slugify(prompt("Store slug ID", default_slug))
    module_id = prompt("Internal moduleId", default_module_id)
    version = prompt("Version", default_version)
    author = prompt("Author", default_author)
    description = prompt("Description", default_desc)
    kind, tags = pick_tags([default_tag], get_catalog_categories())
    discord_url = prompt("Discord post link (optional, https://discord.com/channels/...)", "")

    ZIPS_DIR.mkdir(parents=True, exist_ok=True)
    dest_zip = ZIPS_DIR / f"{slug}.zip"
    shutil.copy2(src, dest_zip)

    icon_url = ""
    if icon_bytes:
        ICONS_DIR.mkdir(parents=True, exist_ok=True)
        (ICONS_DIR / f"{slug}{icon_ext}").write_bytes(icon_bytes)
        icon_url = f"{RAW_BASE}/icons/{slug}{icon_ext}"
        print(f"  + Extracted icon -> {icon_url}")
    icon_url = pick_optional_image("Icon", slug, ICONS_DIR, icon_url)
    banner_url = pick_optional_image("Banner", slug, BANNERS_DIR, "")
    featured = prompt_yes_no("Feature in 'Best from community'?", default=True)

    entry = {
        "id": slug,
        "name": name,
        "moduleId": module_id,
        "version": version,
        "author": author,
        "description": description,
        "kind": kind,
        "tags": tags,
        "paid": False,
        "locked": False,
        "hidden": False,
        "hidden": False,
        "icon": icon_url,
        "banner": banner_url,
        "previews": [],
        "install": "zip",
        "zipUrl": f"{RAW_BASE}/zips/{slug}.zip",
        "size": dest_zip.stat().st_size,
        "updated": int(time.time() * 1000),
        "discordPostUrl": discord_url,
        "postUrl": discord_url,
    }

    cat = load_catalog()
    cat["modules"] = [m for m in cat.get("modules", []) if m.get("id") != slug]
    cat["modules"].append(entry)
    chart = [cid for cid in cat.get("chart", []) if cid != slug]
    if featured:
        chart.insert(0, slug)
    cat["chart"] = chart
    save_catalog(cat)
    print(f"\nAdded/updated '{name}' ({slug}) in store/modules.json.")
    push_changes(f"Store: add/update {name} v{version}")


def add_link_module() -> None:
    print("\n--- Add Link / Web Module ---")
    name = prompt("Module name")
    if not name:
        return
    slug = slugify(prompt("Store slug ID", slugify(name)))
    link = prompt("Module URL (https://...)")
    if not link.startswith("https://"):
        print("URL must start with https://")
        return
    install_mode = prompt("Install type ('link' to install inside Mochy, 'web' to open page)", "link").lower()
    if install_mode not in {"link", "web"}:
        install_mode = "link"
    module_id = prompt("Internal moduleId", slug)
    version = prompt("Version", "1.0.0")
    author = prompt("Author", "Rizky088")
    description = prompt("Description", "")
    kind, tags = pick_tags(["Sources" if install_mode == "link" else "Tools"], get_catalog_categories())
    discord_url = prompt("Discord post link (optional)", "")
    icon_url = pick_optional_image("Icon", slug, ICONS_DIR, "")
    banner_url = pick_optional_image("Banner", slug, BANNERS_DIR, "")
    featured = prompt_yes_no("Feature in 'Best from community'?", default=False)

    entry = {
        "id": slug,
        "name": name,
        "moduleId": module_id,
        "version": version,
        "author": author,
        "description": description,
        "kind": kind,
        "tags": tags,
        "paid": False,
        "locked": False,
        "icon": icon_url,
        "banner": banner_url,
        "previews": [],
        "install": install_mode,
        "link": link,
        "updated": int(time.time() * 1000),
        "discordPostUrl": discord_url,
        "postUrl": discord_url,
    }

    cat = load_catalog()
    cat["modules"] = [m for m in cat.get("modules", []) if m.get("id") != slug]
    cat["modules"].append(entry)
    chart = [cid for cid in cat.get("chart", []) if cid != slug]
    if featured:
        chart.insert(0, slug)
    cat["chart"] = chart
    save_catalog(cat)
    print(f"\nAdded/updated '{name}' ({slug}).")
    push_changes(f"Store: add/update {name}")


def edit_module() -> None:
    cat = load_catalog()
    mods = cat.get("modules", [])
    if not mods:
        print("No modules in store yet.")
        return
    print("\n--- Edit Module ---")
    for i, m in enumerate(mods, 1):
        dlink = m.get("discordPostUrl") or m.get("postUrl") or "(no Discord link)"
        vis = " [HIDDEN]" if m.get("hidden") else ""
        print(f"  {i}. {m.get('name')} [{m.get('id')}]{vis} - {dlink}")
    raw = input("Pick number to edit: ").strip()
    if not raw.isdigit() or not (1 <= int(raw) <= len(mods)):
        return
    m = mods[int(raw) - 1]
    slug = m["id"]
    cur_discord = m.get("discordPostUrl") or m.get("postUrl") or ""
    new_discord = prompt("Discord post link ('clear' to remove)", cur_discord)
    if new_discord.lower() == "clear":
        new_discord = ""
    m["discordPostUrl"] = new_discord
    m["postUrl"] = new_discord
    cur_tags = m.get("tags") if isinstance(m.get("tags"), list) else []
    edited_kind, edited_tags = pick_tags(cur_tags, get_catalog_categories(cat))
    m["kind"] = edited_kind
    m["tags"] = edited_tags
    m["name"] = prompt("Name", m.get("name", ""))
    m["version"] = prompt("Version", m.get("version", ""))
    m["description"] = prompt("Description", m.get("description", ""))
    m["icon"] = pick_optional_image("Icon", slug, ICONS_DIR, m.get("icon", ""))
    m["banner"] = pick_optional_image("Banner", slug, BANNERS_DIR, m.get("banner", ""))
    m["updated"] = int(time.time() * 1000)

    m["hidden"] = prompt_yes_no("Hide module from store?", default=bool(m.get("hidden", False)))
    in_chart = slug in cat.get("chart", [])
    want_chart = prompt_yes_no("Feature in 'Best from community'?", default=in_chart)
    chart = [cid for cid in cat.get("chart", []) if cid != slug]
    if want_chart:
        chart.insert(0, slug)
    cat["chart"] = chart
    save_catalog(cat)
    print(f"\nUpdated '{m['name']}'.")
    push_changes(f"Store: update {m['name']}")


def remove_module() -> None:
    cat = load_catalog()
    mods = cat.get("modules", [])
    if not mods:
        print("No modules in store.")
        return
    print("\n--- Remove Module ---")
    for i, m in enumerate(mods, 1):
        vis = " [HIDDEN]" if m.get("hidden") else ""
        print(f"  {i}. {m.get('name')} [{m.get('id')}]{vis}")
    raw = input("Pick number to remove: ").strip()
    if not raw.isdigit() or not (1 <= int(raw) <= len(mods)):
        return
    m = mods[int(raw) - 1]
    slug = m["id"]
    if not prompt_yes_no(f"Delete '{m.get('name')}' ({slug}) from the store?", default=False):
        return
    cat["modules"] = [x for x in mods if x.get("id") != slug]
    cat["chart"] = [cid for cid in cat.get("chart", []) if cid != slug]
    save_catalog(cat)
    for folder in (ZIPS_DIR, ICONS_DIR, BANNERS_DIR):
        if folder.is_dir():
            for f in folder.glob(f"{slug}.*"):
                f.unlink(missing_ok=True)
    print(f"Removed '{m.get('name')}'.")
    push_changes(f"Store: remove {m.get('name')}")


def toggle_hide_module() -> None:
    cat = load_catalog()
    mods = cat.get("modules", [])
    if not mods:
        print("No modules in store.")
        return
    chart = set(cat.get("chart", []))
    print("\n--- Hide / Unhide Modules ---")
    for i, m in enumerate(mods, 1):
        state = "HIDDEN " if m.get("hidden") else "VISIBLE"
        feat = " ★" if m.get("id") in chart else ""
        print(f"  {i}. [{state}] {m.get('name')} ({m.get('id')}){feat}")
    raw = input("Enter number(s) to toggle (e.g. 1 or 1, 3) [Enter = cancel]: ").strip()
    if not raw:
        return
    tokens = [tok.strip() for tok in re.split(r"[,\s]+", raw) if tok.strip()]
    changed = []
    seen = set()
    for tok in tokens:
        if not tok.isdigit():
            continue
        idx = int(tok) - 1
        if 0 <= idx < len(mods) and idx not in seen:
            seen.add(idx)
            m = mods[idx]
            new_hidden = not bool(m.get("hidden", False))
            m["hidden"] = new_hidden
            m["updated"] = int(time.time() * 1000)
            action = "Hidden" if new_hidden else "Shown"
            changed.append(f"{action} '{m.get('name')}'")
    if not changed:
        print("No valid module numbers selected.")
        return
    save_catalog(cat)
    for line in changed:
        print(f"  -> {line}")
    push_changes("Store: " + ", ".join(changed))


def main() -> None:
    while True:
        print("\n=== Mochy Ext Module Store Manager ===")
        print("  1. Add / Update .zip module (from Downloads)")
        print("  2. Add / Update link or web module")
        print("  3. Edit module (tags, Discord link, banner, hide/show)")
        print("  4. Hide / Unhide module(s)")
        print("  5. Remove module")
        print("  6. Push pending store changes to GitHub")
        print("  0. Exit")
        choice = input("Choose: ").strip()
        if choice == "1":
            add_zip_module()
        elif choice == "2":
            add_link_module()
        elif choice == "3":
            edit_module()
        elif choice == "4":
            toggle_hide_module()
        elif choice == "5":
            remove_module()
        elif choice == "6":
            push_changes("Store: update catalog")
        elif choice == "0":
            break


if __name__ == "__main__":
    main()
