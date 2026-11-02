"""Collect available package notices, without changing original license texts."""
from pathlib import Path
import importlib.metadata as meta
import json
import subprocess
import sys

root=Path(__file__).resolve().parents[1]
parts=['YORU — THIRD-PARTY NOTICES\nCreated by Obscura. Original upstream authorship is retained.\n']
def include(label,path):
    try:
        text=path.read_text(encoding='utf-8',errors='replace')
        parts.append('\n'+'='*72+'\n'+label+'\n'+text+'\n')
    except OSError:pass
for dist in sorted(meta.distributions(),key=lambda d:d.metadata.get('Name','').lower()):
    name=dist.metadata.get('Name','Unknown')+' '+dist.version
    for file in dist.files or []:
        if Path(str(file)).name.lower().startswith(('license','copying','notice')):
            path=Path(dist.locate_file(file))
            if path.is_file():include('Python package: '+name+' / '+path.name,path)
for name in ['LICENSE.txt','LICENSE']:
    p=Path(sys.base_prefix)/name
    if p.exists():include('Python runtime',p)
result=subprocess.run(['cargo','metadata','--locked','--format-version','1'],cwd=root,check=True,capture_output=True,text=True)
for package in json.loads(result.stdout)['packages']:
    name=package['name']+' '+package['version']+' — '+str(package.get('license') or 'see upstream notices')
    folder=Path(package['manifest_path']).parent
    notices=[p for p in folder.iterdir() if p.is_file() and p.name.lower().startswith(('license','copying','notice'))]
    if not notices:parts.append('\nRust crate: '+name+'\nRepository: '+str(package.get('repository') or 'not specified')+'\n')
    for p in notices:include('Rust crate: '+name+' / '+p.name,p)
Path(sys.argv[1]).write_text(''.join(parts),encoding='utf-8')
