# Yoru public-release checklist

Status: planning/audit only. The existing v0.4 binaries are not a completed implementation of this checklist. Do not publish them as the new feature-complete release.

## Blocking publisher decisions
- [ ] Choose metadata architecture: publisher-hosted metadata service or per-user TMDB credentials. Never treat a credential embedded in an APK/EXE as private.
- [ ] Establish a publisher-owned Android release signing identity and keep its private key/password outside Git. Decide how existing differently signed installs migrate, including data backup before uninstall.
- [ ] Choose direct/GitHub distribution, stores, or both; review provider/content permissions and applicable distribution requirements.

## Requested discovery and library features
- [ ] Marvel, DC, fantasy, action, Korean, Indian, Hollywood, highest-rated and newly released movie shelves.
- [ ] Accurate franchise/country classification; do not infer country from dubbed audio.
- [ ] Explicit separation of release date, upcoming date and provider-added date. Date cutoffs, source refresh time, retry/backoff and manual refresh.
- [ ] Genre/country/year/rating/audio filters and sorting.
- [ ] Richer details, cast, related titles and safe external trailers.
- [ ] Local watchlist-based suggestions, not-interested/hide controls and separate upcoming watchlist.
- [ ] Search suggestions, typo tolerance and alternate/original titles.
- [ ] Provider-reported CAM/dub/subtitle/resolution/size labels and optional CAM hiding; do not invent unknown values.
- [ ] Backup/export/import favorites, history and settings with validation, preview and confirmation. Exclude credentials, signed media URLs and authentication headers.
- [ ] Optional local profiles with data isolation and explicit migration.

## Downloads
Existing Android v0.4 source includes queue/progress/pause/resume/removal and cache-only playback. Native device behavior has not been validated here. PC downloads are not implemented.
- [ ] Wi-Fi/unmetered-only preference, storage cap and low-data policy.
- [ ] Clear failure diagnostics, provider-verified fresh-link retry, no content-filter bypass.
- [ ] Offline-readiness verification, corrupt/missing data handling and travel check.
- [ ] Decide and document PC download support rather than silently implying Android feature parity.

## Playback and series
Existing Android source includes immersive mode, Fit/Fill, phone rotation and control lock. Desktop uses external players.
- [ ] Save supported playback-speed, picture-fit and subtitle appearance preferences.
- [ ] Subtitle size/background/timing controls where supported.
- [ ] Configurable 10/30-second double-tap seek on touch devices.
- [ ] Sleep timer and end-of-episode stop.
- [ ] Next episode, watched markers and optional cancelable autoplay countdown.
- [ ] Preserve language/episode/position on retry or alternate release.
- [ ] Loading/error states that do not hide actionable recovery.
- [ ] Device-supported TV voice input with permission-denied/unavailable fallback.
- [ ] Optional new-episode notifications with accurate scheduling/data-source limits and permission controls.
- [ ] Do not add guessed skip-intro timestamps; keep unavailable until a reliable episode-specific timestamp source exists.

## Accessibility, safety and privacy
- [ ] Larger text, high contrast, reduced motion and remote focus audit.
- [ ] Low-data artwork and quality preferences without overriding explicit user choices silently.
- [ ] Inappropriate-title feedback with clear local/reporting behavior; no unsupported child-safe claim.
- [ ] Diagnostics export that excludes watch history by default, tokens, cookies, signed URLs and personal paths.
- [ ] Verify all new metadata/recommendation/trailer routes maintain the explicit-content gate before artwork/display/playback/download actions.
- [ ] Publisher-approved privacy notice, attribution/metadata terms, content/provider permissions and support/report contact.

## Release engineering and device gates
- [ ] Publisher-owned signing, non-debuggable Android builds, matching certs, increasing version codes and update migration tests.
- [ ] Authenticated update metadata and actual package-signature validation; HTTPS/checksums alone are not a substitute for publisher authentication.
- [ ] Build/test all advertised platforms; produce the Windows EXE/installer on Windows and test WebView2/VLC/mpv behavior.
- [ ] Native Android playback, seeking, audio/subtitles, rotation, lock/unlock, focus loss, backgrounding and system-bar behavior on supported phone/TV devices.
- [ ] Completed progressive/HLS/DASH downloads with offline/airplane-mode playback, interrupted/resumed/expired links, disk full, removal and notification denial.
- [ ] Test clean install and update with preserved profiles/library data; test backup/import recovery for a signing-key transition.
- [ ] Release notes, real screenshots, integrity manifest, reproducible build inputs and no private files in packages/source.

Cloud accounts, cross-device sync and social features were explicitly recommended for later, not as part of this local-first release. They require separate privacy/hosting/security design if requested.

## Development snapshot 0.5 (PC + mobile)
- Manual discovery refresh, local check time, retained prior feed on failure.
- Optional hide-CAM preference for explicitly labeled releases, and displayed provider-reported size. Unknown recording type remains visible.
- These changes do not implement TMDB, optional account connection, backup, recommendations or the remaining release scope.
- Browser tests use mock transport. Desktop Python tests pass; this does not certify real playback or distribution.
