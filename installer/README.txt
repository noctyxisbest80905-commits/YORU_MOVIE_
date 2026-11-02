YORU — FILTERED DESKTOP SETUP SOURCE
Created by Obscura

STATUS
This archive is a source overlay for your existing full MovieBox-TUI/Yoru project.
There is no compiled Windows EXE or functional installer supplied by this Linux run.
Run the Windows workflow to produce the actual Yoru.exe and Yoru-Setup.exe.

BUILD
Merge files into the matching folders; do not delete the original Cargo/source files.
Workflow: .github/workflows/yoru-windows.yml. A visible identical copy is included
at installer/yoru-windows.yml. Run Actions > Build Yoru Windows Setup > Run workflow.
Download the successful Yoru-Windows-Setup-x64 artifact and extract Yoru-Setup.exe.
Local Windows builds require Rust MSVC, Python 3.12 x64 and NSIS 3.x; run
powershell -File installer\build-setup.ps1 from the full repository.

INSTALLER
Installs Yoru Desktop and its embedded provider backend, adds shortcuts and an
uninstaller. WebView2 is required and is checked/downloaded only if missing.
VLC is optional and off by default; the official installer is signature/hash checked.
No unfiltered original MovieBox TUI is bundled with this filtered installer. During
upgrade, the legacy optional terminal EXE/shortcuts in Yoru's own install folder are
removed; unrelated terminal installations elsewhere are not modified or filtered.

FILTER
Always on. Search/discovery uses title rules, an offline explicit-anime alias index,
and provider details before showing results. Metadata/identity failures hide the
unverified results. Details, release/subtitle requests and player launches are also
gated. The player receives the checked provider release, not arbitrary replacement
mirrors supplied by the UI. Previously identified blocked IDs persist locally.
Known/labelled explicit favorites and history entries are hidden, not erased.
The complete offline index, notices and database licenses are installed alongside
the executable. No new runtime classification service receives your searches.
This filtered build retains Yoru/Obscura, provider and upstream credits and history.

LIMITS
A metadata/title filter cannot guarantee that all explicit or inappropriate content
is blocked. Mislabeling, aliases not in the snapshot, and new content may escape.
This is not a child-safety certification or a tamper-proof parental-control product.
Some legitimate titles can be hidden if metadata is missing or cannot be checked.
Search/discovery may take longer or return fewer results. See safety/README.md.
Watch history stays local; it does not sync with phone/TV. Seek support depends on
streams/players. The legacy storage location is kept to preserve existing data.
Uninstall keeps Yoru desktop personal data; manually clearing app data removes it.

VALIDATION
Python controller/history/content tests and browser UI suites passed in Linux.
The Android native gate has separate JVM tests. Windows EXE packaging, NSIS wizard,
installation/upgrade/uninstall and actual VLC/mpv/WebView2 behavior still require
Windows build/device testing. The Windows app/installer remain unsigned unless the
publisher supplies a legitimate signing certificate. Follow device security policy;
do not disable antivirus or bypass managed-device requirements.
