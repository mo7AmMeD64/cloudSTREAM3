# Manga extension compatibility checker

Checks whether real Mihon/Keiyoushi extension APKs can link against a KodaStream build.

The GitHub workflow `.github/workflows/manga-compat.yml` builds the debug APK, downloads
the store index and a deterministic sample, and compares every referenced member in the
Mihon and extension-library namespaces with what the APK defines.

Run it from **Actions → Manga extension compatibility → Run workflow**. Inputs:

- `store_url`: index URL, defaults to Keiyoushi's `index.pb`.
- `sample_count`: default 40. The sample always contains MangaDex, MangaPlus and at least
  10 Arabic extensions.

Local run:

```bash
pip install "androguard>=4.1,<5"
./gradlew :app:assembleDebug
python tools/manga-compat/check_compat.py \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --store-url https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb \
  --samples 40
```

The script writes `compat-report.md` and `compat-report.json`. It fails only when a
member in `eu.kanade.tachiyomi.` or `uy.kohesive.injekt.` is missing; third-party library
gaps (for example OkHttp 5 members absent from OkHttp 4.12) are listed as warnings.
