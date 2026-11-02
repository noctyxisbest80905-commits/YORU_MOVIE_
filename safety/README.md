# Yoru always-on explicit-content filtering

The native Android Java gate and PC Python controller check provider details before
returning search/discovery cards, before release/subtitle requests, and again before
playback. Results lacking usable detail metadata or failing identity checks are not
shown. Release selection is checked against the provider response; supplied mirror
URLs cannot replace a checked release. The bundled JavaScript adds a UI-side layer.
There is no in-app disable switch or client-supplied bypass flag.

The shared policy detects adult/sexual labels, common obfuscations, title aliases
and release filenames. The offline explicit-anime index supplies broader title
coverage; ambiguous aliases shared with non-explicit database entries are excluded
(e.g. Hunter x Hunter must remain available). Known blocked provider IDs persist so
renaming a listing or reopening a saved dub does not bypass a known classification.
Favorites/history matching the rules are hidden, not silently erased from storage.
Previously unclassified saved entries are checked before details/playback; their
stored titles alone may not reveal mislabeling. This is not a guarantee against all
explicit or inappropriate content and is not a parental-control system.

No user queries are sent to a new classification service: the title index is local.
Only the existing selected media provider is queried at runtime. The snapshot is
not automatically updated, and mislabeled or newly released titles can be missed.
The default search/discovery check uses at most 3 concurrent detail requests and a
30-second batch budget; unverified entries are omitted rather than shown unchecked.
This may reduce results and add loading time. Checked metadata is cached for 5 minutes.

## Data and licensing

See ANIME-INDEX-NOTICE.txt and ANIME-INDEX-LICENSE.txt. The derivative database is
ODbL-1.0 + DbCL-1.0; the separate application code retains its existing license.
The complete derivative data is adult_anime_index.json. To refresh it, obtain a
permitted published anime-offline-database release and run build_index.py, review
classification/title collisions, then run generate.py and all regression tests.
The current extraction script records the chosen source release in the manifest;
update that source URL when changing snapshots. Never silently relabel the app as
child-safe or promise that keyword/metadata matching catches every explicit title.

## Harem filtering and featured anime search

The offline index also includes anime records tagged harem, reverse harem, female
harem and related labels. Exact alias matches may hide unrelated works with the
same title; aliases shared with unclassified anime are excluded to reduce this risk.
The primary Sword Art Online record is excluded from the harem denylist at the
user’s explicit request; explicit labels, flags and the explicit index still apply.
Harem and explicit material are not equivalent classifications. Metadata and title
matching can miss titles and cannot guarantee complete filtering.

Searching exactly “anime” or “animation” uses a small curated list of title searches
against the selected provider. It does not fabricate results and only shows provider
entries passing the existing details gate. AOT, SAO and DBZ are expanded to their
full names. Obvious 2023/Netflix live-action One Piece matches are omitted, but
providers often omit genre metadata, so other adaptations can still appear. A
provider can be unavailable or omit individual series. These app-side filters do
not remove or alter any catalog on third-party provider servers.
