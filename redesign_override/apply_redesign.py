from pathlib import Path
import base64
import hashlib
import io
import shutil
import zipfile

root = Path("project")
over = Path("redesign_override")

if not root.exists():
    raise SystemExit("project directory not found")

parts = []
for i in range(1, 5):
    path = over / f"v2_payload_{i}.b64"
    if not path.exists():
        raise SystemExit(f"Missing payload part: {path}")
    # Base64 permits whitespace; strip it so wrapping or an accidental space
    # cannot corrupt the reconstructed archive.
    parts.append("".join(path.read_text().split()))

encoded = "".join(parts)
raw = base64.b64decode(encoded, validate=True)
expected_sha256 = "5a6ccd47b10958b589f88a7479cd5db68c19cab5d8b811345831d3a3670c764b"
actual_sha256 = hashlib.sha256(raw).hexdigest()
if actual_sha256 != expected_sha256:
    raise SystemExit(
        f"HesabYar v2 payload checksum mismatch: {actual_sha256} != {expected_sha256}"
    )

with zipfile.ZipFile(io.BytesIO(raw)) as archive:
    archive.testzip()
    archive.extractall(root)

# Optional post-payload hotfix directory. Files placed here mirror project paths.
fix_root = over / "v2_fix"
if fix_root.exists():
    for src in fix_root.rglob("*"):
        if src.is_file():
            dst = root / src.relative_to(fix_root)
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)

required = [
    "android.permission.READ_SMS",
    "BankRegistry",
    "discoverLatestAccounts",
    "is_excluded",
    "حساب اشخاص",
    "ریال",
    "deleteTransaction",
]
all_text = "\n".join(
    p.read_text(errors="ignore")
    for p in (root / "app/src/main").rglob("*")
    if p.is_file()
)
for token in required:
    if token not in all_text:
        raise SystemExit(f"Missing banking-v2 token: {token}")

for forbidden in ["مبلغ (تومان)", "amountToman", "دنگ‌ها و اشخاص"]:
    if forbidden in all_text:
        raise SystemExit(f"Legacy token remains: {forbidden}")

print(f"HesabYar banking-v2 payload applied: {actual_sha256}")
