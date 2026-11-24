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


def resolve_local_file(raw: str) -> pathlib.Path | None:
    if not raw:
        return None
    p = pathlib.Path(raw).expanduser()
    if p.is_file():
        return p
    for d in DOWNLOAD_DIRS:
        alt = d / raw
        if alt.is_file():
            return alt
    return None


def pick_zip_file(hint_terms: list[str] | None = None) -> pathlib.Path | None:
    zips = list_recent_files((".zip",))
    if not zips:
        choice = input("No .zip files found in Downloads. Paste full path to .zip: ").strip()
        return resolve_local_file(choice)

    default_idx = 1
    if hint_terms:
        clean_terms = [t.lower() for t in hint_terms if t and len(t) >= 3]
        for i, z in enumerate(zips, 1):
            zlow = z.stem.lower()
            if any(t in zlow for t in clean_terms):
                default_idx = i
                break

    print("\n--- Pick a .zip file ---")
    for i, z in enumerate(zips, 1):
        kb = z.stat().st_size // 1024
        mark = "  <-- default" if i == default_idx else ""
        print(f"  {i}. {z.name} ({kb} KB){mark}")
    choice = input(f"Pick number, paste path, or Enter for #{default_idx} ('c' to cancel): ").strip()
    if choice.lower() in {"c", "q", "cancel"}:
        return None
    if not choice:
        return zips[default_idx - 1]
    if choice.isdigit() and 1 <= int(choice) <= len(zips):
        return zips[int(choice) - 1]
    found = resolve_local_file(choice)
    if not found:
        print(f"File not found: {choice}")
    return found


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
    p = resolve_local_file(raw)
    if p and p.is_file():
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
                0 if os.path.basename(n).lower() in {"look.json", "manifest.json", "module.json", "info.json"} else 1,
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


def find_existing_module(mods: list[dict], slug: str = "", module_id: str = "", name: str = "") -> dict | None:
    cand_ids = {x.strip().lower() for x in (slug, module_id) if x and x.strip()}
    cand_name = name.strip().lower()
    for m in mods:
        mid = str(m.get("moduleId") or "").strip().lower()
        sid = str(m.get("id") or "").strip().lower()
        if cand_ids and (mid in cand_ids or sid in cand_ids):
            return m
    if cand_name:
        for m in mods:
            if str(m.get("name") or "").strip().lower() == cand_name:
                return m
    return None


def push_changes(message: str, auto_confirm: bool = False) -> None:
    if not auto_confirm and not prompt_yes_no("Push changes to GitHub now?", default=True):
        print("Saved locally. Run option 7 (or `mochy-store push`) later to push.")
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


def apply_zip_update(cat: dict, m: dict, src: pathlib.Path) -> None:
    slug = m["id"]
    meta, icon_bytes, icon_ext, _ = inspect_zip(src)
    new_module_id = str(meta.get("id") or meta.get("moduleId") or "").strip()
    new_version = str(meta.get("version") or "").strip()
    new_desc = str(meta.get("description") or "").strip()

    old_ver = str(m.get("version") or "")
    old_mid = str(m.get("moduleId") or "")
    old_size_kb = int(m.get("size") or 0) // 1024
    new_size = src.stat().st_size

    print(f"\nUpdating '{m.get('name')}' ({slug}) from {src.name}:")
    print(f"  Version  : {old_ver or '?'} -> {new_version or old_ver or '?'}")
    if new_module_id and new_module_id != old_mid:
        print(f"  moduleId : {old_mid} -> {new_module_id}")
    print(f"  Size     : {old_size_kb} KB -> {new_size // 1024} KB")

    version = prompt("Version", new_version or old_ver or "1.0.0")

    ZIPS_DIR.mkdir(parents=True, exist_ok=True)
    dest_zip = ZIPS_DIR / f"{slug}.zip"
    shutil.copy2(src, dest_zip)

    m["version"] = version
    if new_module_id:
        m["moduleId"] = new_module_id
    m["install"] = "zip"
    m["zipUrl"] = f"{RAW_BASE}/zips/{slug}.zip"
    m["size"] = dest_zip.stat().st_size
    m["updated"] = int(time.time() * 1000)

    if new_desc and new_desc != str(m.get("description") or "").strip():
        if prompt_yes_no("Description changed in zip. Update store description too?", default=True):
            m["description"] = new_desc

    if icon_bytes:
        if not m.get("icon") or prompt_yes_no("Zip includes an icon. Refresh store icon too?", default=False):
            ICONS_DIR.mkdir(parents=True, exist_ok=True)
            (ICONS_DIR / f"{slug}{icon_ext}").write_bytes(icon_bytes)
            m["icon"] = f"{RAW_BASE}/icons/{slug}{icon_ext}"
            print(f"  + Updated icon -> {m['icon']}")

    save_catalog(cat)
    print(f"\nUpdated '{m['name']}' to v{version} (tags, Discord link, banner & visibility preserved).")
    push_changes(f"Store: update {m['name']} to v{version}")


def quick_update_module(arg: str = "") -> None:
    cat = load_catalog()
    mods = cat.get("modules", [])
    if not mods:
        print("No modules in store yet.")
        return

    # Direct zip argument: `mochy-store update MyModule.zip`
    zip_from_arg = resolve_local_file(arg) if arg and arg.lower().endswith(".zip") else None
    if zip_from_arg:
        meta, _, _, _ = inspect_zip(zip_from_arg)
        matched = find_existing_module(
            mods,
            slug=slugify(str(meta.get("id") or meta.get("moduleId") or zip_from_arg.stem)),
            module_id=str(meta.get("id") or meta.get("moduleId") or ""),
            name=str(meta.get("name") or meta.get("title") or ""),
        )
        if matched and prompt_yes_no(f"Auto-matched '{matched.get('name')}' ({matched.get('id')}). Update it?", default=True):
            apply_zip_update(cat, matched, zip_from_arg)
            return

    filtered = mods
    if arg and not zip_from_arg:
        q = arg.lower()
        matches = [m for m in mods if q in str(m.get("name", "")).lower() or q in str(m.get("id", "")).lower() or q in str(m.get("moduleId", "")).lower()]
        if len(matches) == 1:
            m = matches[0]
            print(f"\nMatched: {m.get('name')} v{m.get('version', '?')} [{m.get('install', 'zip')}]")
            if m.get("install") == "zip":
                src = pick_zip_file([m.get("id", ""), m.get("name", ""), m.get("moduleId", "")])
                if src:
                    apply_zip_update(cat, m, src)
            else:
                _quick_update_link(cat, m)
            return
        if matches:
            filtered = matches

    print("\n--- Quick Update Existing Module ---")
    for i, m in enumerate(filtered, 1):
        vis = " [HIDDEN]" if m.get("hidden") else ""
        kind = m.get("install", "zip")
        ver = m.get("version") or "?"
        print(f"  {i}. [{kind}] {m.get('name')} (v{ver}){vis}")
    print("  a. Auto-detect module from a .zip in Downloads")

    raw = input("Pick number, type name filter, or 'a' [Enter = cancel]: ").strip()
    if not raw:
        return

    if raw.lower() == "a":
        src = zip_from_arg or pick_zip_file()
        if not src:
            return
        meta, _, _, _ = inspect_zip(src)
        matched = find_existing_module(
            mods,
            slug=slugify(str(meta.get("id") or meta.get("moduleId") or src.stem)),
            module_id=str(meta.get("id") or meta.get("moduleId") or ""),
            name=str(meta.get("name") or meta.get("title") or ""),
        )
        if not matched:
            print("Could not auto-match that zip to an existing store entry. Pick the module to update:")
            for i, m in enumerate(mods, 1):
                print(f"  {i}. {m.get('name')} [{m.get('id')}]")
            pick = input("Pick number [Enter = cancel]: ").strip()
            if not pick.isdigit() or not (1 <= int(pick) <= len(mods)):
                return
            matched = mods[int(pick) - 1]
        apply_zip_update(cat, matched, src)
        return

    if raw.isdigit() and 1 <= int(raw) <= len(filtered):
        m = filtered[int(raw) - 1]
    else:
        q = raw.lower()
        hits = [m for m in filtered if q in str(m.get("name", "")).lower() or q in str(m.get("id", "")).lower()]
        if not hits:
            print(f"No module matching '{raw}'.")
            return
        m = hits[0]

    if m.get("install") == "zip":
        src = zip_from_arg or pick_zip_file([m.get("id", ""), m.get("name", ""), m.get("moduleId", "")])
        if not src:
            return
        apply_zip_update(cat, m, src)
    else:
        _quick_update_link(cat, m)


def _quick_update_link(cat: dict, m: dict) -> None:
    print(f"\nUpdating link/web module '{m.get('name')}' ({m.get('id')}):")
    new_link = prompt("Module URL", str(m.get("link") or ""))
    if new_link and not new_link.startswith("https://"):
        print("URL must start with https://")
        return
    m["link"] = new_link
    m["version"] = prompt("Version", str(m.get("version") or "1.0.0"))
    m["description"] = prompt("Description", str(m.get("description") or ""))
    m["updated"] = int(time.time() * 1000)
    save_catalog(cat)
    print(f"\nUpdated '{m['name']}' to v{m['version']}.")
    push_changes(f"Store: update {m['name']} to v{m['version']}")


def sync_zips_metadata() -> None:
    cat = load_catalog()
    fixed = []
    for m in cat.get("modules", []):
        if m.get("install") != "zip":
            continue
        zp = ZIPS_DIR / f"{m.get('id', '')}.zip"
        if not zp.is_file():
            continue
        meta, _, _, _ = inspect_zip(zp)
        real_id = str(meta.get("id") or meta.get("moduleId") or "").strip()
        real_ver = str(meta.get("version") or "").strip()
        real_size = zp.stat().st_size
        changes = []
        if real_id and m.get("moduleId") != real_id:
            changes.append(f"moduleId -> {real_id}")
            m["moduleId"] = real_id
        if real_ver and m.get("version") != real_ver:
            changes.append(f"version -> {real_ver}")
            m["version"] = real_ver
        if m.get("size") != real_size:
            changes.append(f"size -> {real_size // 1024} KB")
            m["size"] = real_size
        if changes:
            m["updated"] = int(time.time() * 1000)
            fixed.append(f"{m.get('name')}: " + ", ".join(changes))
    if not fixed:
        print("All store .zip entries already match their manifest metadata.")
        return
    save_catalog(cat)
    print("Synced metadata:")
    for line in fixed:
        print(f"  -> {line}")
    push_changes("Store: sync zip metadata")


def add_zip_module() -> None:
    src = pick_zip_file()
    if not src:
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

    cat = load_catalog()
    existing = find_existing_module(cat.get("modules", []), slug=default_slug, module_id=default_module_id, name=default_name)
    if existing:
        print(f"\nFound existing store entry '{existing.get('name')}' ({existing.get('id')}).")
        if prompt_yes_no("Use Quick Update (keeps tags, Discord link, banner & visibility)?", default=True):
            apply_zip_update(cat, existing, src)
            return

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
    name = prompt("Module name", existing.get("name", default_name) if existing else default_name)
    slug = slugify(prompt("Store slug ID", existing.get("id", default_slug) if existing else default_slug))
    module_id = prompt("Internal moduleId", default_module_id)
    version = prompt("Version", default_version)
    author = prompt("Author", existing.get("author", default_author) if existing else default_author)
    description = prompt("Description", default_desc or (existing.get("description", "") if existing else ""))
    default_tags = existing.get("tags") if (existing and isinstance(existing.get("tags"), list)) else [default_tag]
    kind, tags = pick_tags(default_tags, get_catalog_categories(cat))
    cur_discord = (existing.get("discordPostUrl") or existing.get("postUrl") or "") if existing else ""
    discord_url = prompt("Discord post link (optional, https://discord.com/channels/...)", cur_discord)

    ZIPS_DIR.mkdir(parents=True, exist_ok=True)
    dest_zip = ZIPS_DIR / f"{slug}.zip"
    shutil.copy2(src, dest_zip)

    icon_url = existing.get("icon", "") if existing else ""
    if icon_bytes:
        ICONS_DIR.mkdir(parents=True, exist_ok=True)
        (ICONS_DIR / f"{slug}{icon_ext}").write_bytes(icon_bytes)
        icon_url = f"{RAW_BASE}/icons/{slug}{icon_ext}"
        print(f"  + Extracted icon -> {icon_url}")
    icon_url = pick_optional_image("Icon", slug, ICONS_DIR, icon_url)
    banner_url = pick_optional_image("Banner", slug, BANNERS_DIR, existing.get("banner", "") if existing else "")
    in_chart = slug in cat.get("chart", []) if existing else True
    featured = prompt_yes_no("Feature in 'Best from community'?", default=in_chart)

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
        "hidden": bool(existing.get("hidden", False)) if existing else False,
        "icon": icon_url,
        "banner": banner_url,
        "previews": existing.get("previews", []) if existing else [],
        "install": "zip",
        "zipUrl": f"{RAW_BASE}/zips/{slug}.zip",
        "size": dest_zip.stat().st_size,
        "updated": int(time.time() * 1000),
        "discordPostUrl": discord_url,
        "postUrl": discord_url,
    }

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
        "hidden": False,
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


def print_help() -> None:
    print("Usage: mochy-store [command]")
    print("  mochy-store                   Open interactive menu")
    print("  mochy-store update [name|zip] Quick-update an existing module (alias: u)")
    print("  mochy-store add               Add a new .zip module from Downloads")
    print("  mochy-store edit              Edit module metadata")
    print("  mochy-store hide              Hide / unhide module(s)")
    print("  mochy-store sync              Sync all store/zips/*.zip manifest metadata")
    print("  mochy-store push              Commit & push pending store/ changes")


def main() -> None:
    if len(sys.argv) > 1:
        cmd = sys.argv[1].strip().lower()
        arg = " ".join(sys.argv[2:]).strip()
        if cmd in {"update", "u"}:
            quick_update_module(arg)
        elif cmd in {"add", "a"}:
            add_zip_module()
        elif cmd in {"edit", "e"}:
            edit_module()
        elif cmd in {"hide", "h"}:
            toggle_hide_module()
        elif cmd == "sync":
            sync_zips_metadata()
        elif cmd == "push":
            push_changes("Store: update catalog", auto_confirm=True)
        else:
            print_help()
        return

    while True:
        print("\n=== Mochy Ext Module Store Manager ===")
        print("  1. Quick update existing module (.zip or link)")
        print("  2. Add new .zip module (from Downloads)")
        print("  3. Add new link / web module")
        print("  4. Edit module (tags, Discord link, banner, hide/show)")
        print("  5. Hide / Unhide module(s)")
        print("  6. Remove module")
        print("  7. Push pending store changes to GitHub")
        print("  8. Sync all .zip manifest metadata")
        print("  9. Dump all module versions")
        print("  0. Exit")
        choice = input("Choose: ").strip()
        if choice == "1":
            quick_update_module()
        elif choice == "2":
            add_zip_module()
        elif choice == "3":
            add_link_module()
        elif choice == "4":
            edit_module()
        elif choice == "5":
            toggle_hide_module()
        elif choice == "6":
            remove_module()
        elif choice == "7":
            push_changes("Store: update catalog")
        elif choice == "8":
            sync_zips_metadata()
        elif choice == "9":
            cmd_versions()
        elif choice == "0":
            break




def cmd_versions(args=None):
    """Dump current versions of all modules in store/modules.json."""
    import json as _json
    from pathlib import Path as _Path

    args = list(args or [])
    store_json = _Path(__file__).resolve().parent / "modules.json"
    if not store_json.exists():
        print(f"[ERR] {store_json} not found")
        return

    raw = _json.loads(store_json.read_text(encoding="utf-8"))
    if isinstance(raw, list):
        mods = raw
    elif isinstance(raw, dict):
        mods = raw.get("modules") or raw.get("items") or raw.get("entries") or []
    else:
        mods = []

    if not mods:
        print("No modules found in store/modules.json.")
        return

    as_json = any(a in ("--json", "-j") for a in args)
    as_plain = any(a in ("--plain", "-p") for a in args)
    save_path = None
    for idx, a in enumerate(args):
        if a in ("--save", "-s"):
            if idx + 1 < len(args) and not args[idx + 1].startswith("-"):
                save_path = _Path(args[idx + 1])
            else:
                save_path = _Path("/sdcard/Download/mochy-store-versions.txt")

    if as_json:
        out_map = {str(m.get("id", "")): str(m.get("version", "?")) for m in mods}
        output = _json.dumps(out_map, indent=2, ensure_ascii=False)
    elif as_plain:
        lines = []
        for m in mods:
            mid = str(m.get("id", "?"))
            ver = str(m.get("version", "?"))
            flag = " [hidden]" if m.get("hidden") else ""
            lines.append(f"{mid}: {ver}{flag}")
        output = "\n".join(lines)
    else:
        rows = []
        for i, m in enumerate(mods, 1):
            mid = str(m.get("id", "?"))
            name = str(m.get("name", mid))
            ver = str(m.get("version", "?"))
            tags = []
            if m.get("hidden"):
                tags.append("hidden")
            if m.get("featured"):
                tags.append("featured")
            status = ", ".join(tags) if tags else "visible"
            rows.append((str(i), mid, name, ver, status))

        w_i = max(len("#"), max(len(r[0]) for r in rows))
        w_id = max(len("ID"), max(len(r[1]) for r in rows))
        w_name = max(len("NAME"), max(len(r[2]) for r in rows))
        w_ver = max(len("VERSION"), max(len(r[3]) for r in rows))
        w_st = max(len("STATUS"), max(len(r[4]) for r in rows))

        hdr = f"{'#':>{w_i}}  {'ID':<{w_id}}  {'NAME':<{w_name}}  {'VERSION':<{w_ver}}  {'STATUS':<{w_st}}"
        sep = "-" * len(hdr)
        lines = [hdr, sep]
        for r in rows:
            lines.append(f"{r[0]:>{w_i}}  {r[1]:<{w_id}}  {r[2]:<{w_name}}  {r[3]:<{w_ver}}  {r[4]:<{w_st}}")
        lines.append(sep)
        lines.append(f"Total: {len(rows)} modules")
        output = "\n".join(lines)

    print(output)
    if save_path is not None:
        save_path.parent.mkdir(parents=True, exist_ok=True)
        save_path.write_text(output + "\n", encoding="utf-8")
        print(f"\n[OK] Saved version dump to {save_path}")


if __name__ == "__main__":
    import sys as _sys
    if len(_sys.argv) > 1 and _sys.argv[1].lower() in ("versions", "version", "dump", "v"):
        cmd_versions(_sys.argv[2:])
        _sys.exit(0)

    main()
