"""Generated explicit-content rules. Not an image classifier or parental-control system."""
import json
import re
import unicodedata
from pathlib import Path
POLICY = json.loads('{"version":2,"message":"Blocked by Yoru\'s anime and explicit-content filter.","unverified":"This title could not be checked safely. Try again later or choose another provider.","phrases":["hentai","hentais","hentay","hen tai","porn","porno","pornography","pornographic","xxx","x rated","nsfw","erotica","erotic anime","erotic animation","erotic movie","erotic film","sexually explicit","explicit sexual","adult video","adult videos","adult movie","adult movies","adult film","adult films","adult anime","adult only","adults only","rule 34","rule34","18\\u7981","\\u6210\\u4eba\\u5411\\u3051","\\u30a2\\u30c0\\u30eb\\u30c8","\\u30a8\\u30ed\\u30a2\\u30cb\\u30e1","\\u30d8\\u30f3\\u30bf\\u30a4","\\u91cc\\u756a","\\u88cf\\u756a","\\u0445\\u0435\\u043d\\u0442\\u0430\\u0439","\\u043f\\u043e\\u0440\\u043d\\u043e","\\ud5e8\\ud0c0\\uc774","\\ud3ec\\ub974\\ub178","\\u8272\\u60c5\\u52d5\\u6f2b","\\u8272\\u60c5\\u52a8\\u753b","\\u6210\\u4eba\\u52a8\\u6f2b","\\u6210\\u4eba\\u52d5\\u756b","\\u6210\\u4eba\\u52d5\\u753b","\\u30dd\\u30eb\\u30ce"],"known_titles":["boku no pico","pico to chico","pico x coco x chico","bible black","itadaki seieki","oni chichi","resort boin","tsuma shibori","ane yome quartet","jitaku keibiin","shoujo ramune","kuroinu kedakaki seijo wa hakudaku ni somaru","ane jiru","swamp stamp","momoiro bouenkyou","savita bhabhi"],"category_labels":["adult","adults","18+","18 plus","r18","r 18","rx","hentai","ecchi","erotica","erotic","porn","porno","pornography","pornographic","xxx","nsfw","hardcore","softcore","sexually explicit"],"flag_fields":["adult","is_adult","isAdult","is_hentai","isHentai","is_porn","pornographic","sexually_explicit","is_erotic","isErotic"],"text_fields":["title","name","original_title","originalTitle","alternative_titles","alternate_titles","aliases","description","synopsis","summary","tagline","filename","genres","genre","tags","categories","category","labels"],"category_fields":["genres","genre","tags","categories","category","labels"],"title_fields":["title","name","original_title","originalTitle","alternative_titles","alternate_titles","aliases","filename"],"harem_category_labels":["harem","female harem","male harem","reverse harem","reverse-harem","mixed gender harem","mixed harem","yuri harem"]}')

class Blocked(ValueError):
    def __init__(self): super().__init__(POLICY['message'])

def normalize(text):
    value = unicodedata.normalize('NFKD', str(text)).lower()
    value = ''.join(c for c in value if unicodedata.category(c) not in {'Mn', 'Me', 'Cf'})
    value = value.translate(str.maketrans({'0':'o','1':'i','3':'e','4':'a','5':'s','7':'t','@':'a','$':'s'}))
    return ' '.join(re.sub(r'[^\w]+', ' ', value.replace('_',' '), flags=re.UNICODE).split())

def contains(text, phrase):
    a, b = normalize(text), normalize(phrase)
    # CJK phrases do not use Latin word boundaries.
    return b in a if any(ord(c)>127 for c in b) else (' '+b+' ') in (' '+a+' ')

def blocked_text(text):
    raw = unicodedata.normalize('NFKC', str(text)).lower()
    if re.search(r'(?<!\w)h[\W_]*[e3][\W_]*n[\W_]*t[\W_]*[a4@][\W_]*[i1](?:s)?(?!\w)',raw): return True
    return any(contains(text,p) for p in POLICY['phrases'])

def strings(value):
    if isinstance(value,str): return [value]
    if isinstance(value,list): return [s for x in value for s in strings(x)]
    if isinstance(value,dict): return [s for k,x in value.items() if k not in {'id','url','poster_url','image','poster','value'} for s in strings(x)]
    return []

ANIME_INDEX = json.loads((Path(__file__).resolve().parent / 'adult_anime_index.json').read_text(encoding='utf-8'))
ANIME_TITLES = frozenset(normalize(x) for x in ANIME_INDEX['titles'])
ANIME_LONG = tuple(x for x in ANIME_TITLES if len(x)>=12 and len(x.split())>=2)
HAREM_TITLES = frozenset(normalize(x) for x in ANIME_INDEX.get('harem_titles',[]))
def anime_context(value):
    return any(contains(s,p) for field in ('genres','genre','tags','category','categories') for s in strings(value.get(field)) for p in ('anime','animation','hentai')) or any(contains(s,p) for s in strings(value.get('title')) for p in ('anime edition','the animation'))
def indexed_title(text):
    raw = unicodedata.normalize('NFKC',str(text))
    raw = re.sub(r'[\[(][^\])]*(?:hindi|english|japanese|dubbed|subbed|1080|720|cam|bluray)[^\])]*[\])]', ' ',raw, flags=re.I)
    value=normalize(raw)
    value=re.sub(r'\s+(?:anime edition|the animation)$','',value)
    return value in ANIME_TITLES or any(value.startswith(t+' ') for t in ANIME_LONG)

def is_requested_sao(value):
    return normalize(re.sub(r'[\[(][^\])]*(?:hindi|english|japanese|dubbed|subbed|1080|720|cam|bluray)[^\])]*[\])]', ' ',str(value.get('title','')),flags=re.I)) == 'sword art online'

def harem_indexed(text):
    raw=unicodedata.normalize('NFKC',str(text))
    raw=re.sub(r'[\[(][^\])]*(?:hindi|english|japanese|dubbed|subbed|1080|720|cam|bluray)[^\])]*[\])]', ' ',raw,flags=re.I)
    return normalize(raw) in HAREM_TITLES

def is_blocked(value, anime=False):
    if not isinstance(value,dict): return False
    if isinstance(value.get('item'),dict) and is_blocked(value['item'],anime): return True
    anime = anime or anime_context(value)
    if anime and any(indexed_title(t) for k in POLICY['title_fields'] for t in strings(value.get(k))): return True
    if not is_requested_sao(value) and any(harem_indexed(t) for k in POLICY['title_fields'] for t in strings(value.get(k))): return True
    for key in POLICY['flag_fields']:
        flag=value.get(key)
        if flag is True or (isinstance(flag,(int,float)) and not isinstance(flag,bool) and flag==1) or (isinstance(flag,str) and flag.lower() in {'true','yes','1'}): return True
    for key in POLICY['text_fields']:
        for text in strings(value.get(key)):
            if blocked_text(text): return True
            if key in POLICY['title_fields']:
                if re.search(r'(?:\[|\()\s*(?:18\s*\+|adult|r\s*-?\s*18|nsfw)\s*(?:\]|\))',unicodedata.normalize('NFKC',text),re.I): return True
                if any(contains(text,p) for p in POLICY['known_titles']): return True
            if key in POLICY['category_fields']:
                if not is_requested_sao(value) and any(normalize(segment)==normalize(p) for segment in re.split(r'[,/|;]',text) for p in POLICY['harem_category_labels']): return True
                if any(normalize(segment)==normalize(p) for segment in re.split(r'[,/|;]',text) for p in POLICY['category_labels']): return True
    return False

def media_key(value):
    if not isinstance(value,dict): return ''
    if isinstance(value.get('item'),dict): return media_key(value['item'])
    id=value.get('id')
    if isinstance(id,dict): return str(id.get('provider',''))+'\n'+str(id.get('value',''))
    if isinstance(value.get('context'),dict):
        c=value['context'];return str(c.get('provider',''))+'\n'+str(c.get('id',''))
    return ''
