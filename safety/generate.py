"""Generate the same policy for Python, JavaScript and Android Java."""
from pathlib import Path
import json
import shutil
ROOT=Path(__file__).resolve().parents[1]
policy=json.dumps(json.loads((ROOT/'safety/policy.json').read_text()),ensure_ascii=True,separators=(',',':'))
# Templates are checked into safety/ so every future source build is reproducible.
python=(ROOT/'safety/content_safety.py.template').read_text().replace('__POLICY__',repr(policy))
index=json.dumps(json.loads((ROOT/'safety/adult_anime_index.json').read_text()),ensure_ascii=True,separators=(',',':'))
js=(ROOT/'safety/content-safety.js.template').read_text().replace('__POLICY__',policy).replace('__INDEX__',index)
java=(ROOT/'safety/ContentSafety.java.template').read_text().replace('__POLICY__',json.dumps(policy))
if (ROOT/'desktop').exists(): (ROOT/'desktop/content_safety.py').write_text(python)
for location in ['desktop/web','android-mobile/app/src/main/assets','android-tv/app/src/main/assets']:
 if (ROOT/location).exists(): (ROOT/location/'content-safety.js').write_text(js)
for platform in ['mobile','tv']:
 target=ROOT/f'android-{platform}/app/src/main/java/com/moviebox/tv/ContentSafety.java'
 if target.parent.exists(): target.write_text(java)
print('Generated matching PC, phone and TV content rules')

for folder in ['desktop','android-mobile/app/src/main/assets','android-tv/app/src/main/assets']:
 if not (ROOT/folder).exists(): continue
 for file in ['adult_anime_index.json','ANIME-INDEX-NOTICE.txt','ANIME-INDEX-LICENSE.txt']:shutil.copy2(ROOT/'safety'/file,ROOT/folder/file)
