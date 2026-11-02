# Yoru filtered desktop — validation

## Passed in Linux

- 55 Python tests, including existing API/settings/history behavior and new content
  rules, metadata checks, persistence, known-ID/dub checks, safe release replacement,
  forbidden/missing/mismatched context denial, and no unsafe player launch.
- Shared policy corpus: 118 allow/block cases, including ordinary anime and a
  Hunter x Hunter alias collision. Same corpus passed Python, JavaScript and Java.
- Browser safety tests passed PC, phone and TV layouts; rejected titles/posters,
  search requests, details and playback were checked with mocked native transport.
- Existing PC UI, watch-history UI, mobile-touch and TV-remote suites passed.
- Live MovieBox anime search page sample: raw 10 titles, filtered 6. Four known
  explicit problem titles were removed. Fresh live detail metadata was fetched for
  retained titles. No media was played. This does not prove every remaining title
  or every provider is correctly classified.
- Installer section references and matching workflow copies checked statically.
  No unfiltered terminal is bundled in the filtered installer source.

## Not completed here

There is no compiled Windows Yoru.exe or Yoru-Setup.exe in this source update.
Run the included Windows workflow to build them. NSIS compilation, PowerShell on
Windows, installation/upgrade/uninstall, actual WebView2 integration and VLC/mpv
playback/resume still require Windows build/device testing. Android has separate
32-test native JVM suites, but actual Android installation/Media3 playback also
remains device testing rather than proven by browser/JVM tests.

Filtering is based on metadata, labels and an offline explicit-anime title index,
not video/image inspection. It cannot guarantee detection of all explicit content.
Unknown, newly released and mislabeled material may escape, while unavailable
metadata can cause legitimate results to be omitted. There is no UI disable switch,
but this is not a tamper-proof parental-control or system-wide filtering product.
