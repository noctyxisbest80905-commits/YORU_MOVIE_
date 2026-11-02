"""Derive an ODbL/DbCL explicit-anime alias index from a published offline database.
Usage: python safety/build_index.py path/to/anime-offline-database-minified.json
Only title aliases and explicit-tag classifications are retained, never images.
"""
from pathlib import Path
import hashlib,json,sys,re,unicodedata
source=Path(sys.argv[1]);db=json.loads(source.read_text());rows=[r for r in db['data'] if set(r.get('tags',[]))&{'hentai','pornography'}]
# Harem classification is distinct from explicit classification. The user expressly
# requested Sword Art Online, so exclude that exact primary record from the harem
# denylist only; explicit flags and explicit-title rules always remain in force.
harem_tags={'harem','female harem','male harem','reverse harem','reverse-harem','mixed gender harem','mixed harem','yuri harem','harem.'}
harem_rows=[r for r in db['data'] if set(r.get('tags',[])) & harem_tags and r.get('title','').casefold() != 'sword art online']
sao_rows=[r for r in db['data'] if r.get('title','').casefold() == 'sword art online']
# Keep only exact title aliases; avoid tag/plot text in the distributed database.
def normalized(t):
    s=unicodedata.normalize('NFKD',t).lower();s=''.join(c for c in s if unicodedata.category(c) not in {'Mn','Me','Cf'});s=s.translate(str.maketrans({'0':'o','1':'i','3':'e','4':'a','5':'s','7':'t','@':'a','$':'s'}));return ' '.join(re.sub(r'[^\w]+',' ',s.replace('_',' ')).split())
nonexplicit={normalized(t) for r in db['data'] if not(set(r.get('tags',[]))&{'hentai','pornography'}) for t in [r.get('title','')]+r.get('synonyms',[]) if isinstance(t,str)}
all_titles={str(t).strip() for r in rows for t in [r.get('title','')]+r.get('synonyms',[]) if isinstance(t,str) and len(t.strip())>=4}
titles=sorted(t for t in all_titles if normalized(t) not in nonexplicit)

harem_raw={str(t).strip() for r in harem_rows for t in [r.get('title','')]+r.get('synonyms',[]) if isinstance(t,str) and len(t.strip())>=4}
harem_allowed={normalized(t) for r in db['data'] if not(set(r.get('tags',[])) & harem_tags) for t in [r.get('title','')]+r.get('synonyms',[]) if isinstance(t,str)}
harem_allowed.update(normalized(t) for r in sao_rows for t in [r.get('title','')]+r.get('synonyms',[]) if isinstance(t,str))
harem_titles=sorted(t for t in harem_raw if normalized(t) not in harem_allowed)
result={'harem_titles':harem_titles,'harem_alias_count':len(harem_titles),'harem_records':len(harem_rows),'harem_classification_tags':sorted(harem_tags),'harem_exception':'Sword Art Online primary record only; explicit checks still apply','version':1,'source':'https://github.com/manami-project/anime-offline-database','source_release':'https://github.com/manami-project/anime-offline-database/releases/tag/2026-27','source_last_update':db['lastUpdate'],'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'license':'ODbL-1.0 and DbCL-1.0','classification_tags':['hentai','pornography'],'explicit_anime_records':len(rows),'alias_count':len(titles),'ambiguous_aliases_excluded':len(all_titles)-len(titles),'titles':titles}
(Path(__file__).parent/'adult_anime_index.json').write_text(json.dumps(result,ensure_ascii=False,separators=(',',':'))+'\n')
print('Offline index:',len(rows),'explicit-classified anime records;',len(titles),'title aliases. No images or plots retained.')
