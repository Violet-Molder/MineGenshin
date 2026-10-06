# -*- coding: utf-8 -*-
import json, io, shutil

P = r'E:\MCMOD\MineGenshin-26.2\src\main\resources\assets\minegenshin\character\sandrone\sandrone.animation.json'
shutil.copyfile(P, P + '.backup')

with io.open(P, 'r', encoding='utf-8') as f:
    data = json.load(f)

counts = {'timeline': 0, 'vector_str': 0}

def clean(node):
    if isinstance(node, dict):
        if 'timeline' in node:
            del node['timeline']
            counts['timeline'] += 1
        for k, v in list(node.items()):
            if k == 'vector' and isinstance(v, list):
                changed = False
                new = []
                for item in v:
                    if isinstance(item, str):
                        new.append(0.0)
                        changed = True
                    else:
                        new.append(item)
                if changed:
                    counts['vector_str'] += len(v)
                node['vector'] = new
            else:
                clean(v)
    elif isinstance(node, list):
        for item in node:
            clean(item)

clean(data)

with io.open(P, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(data, f, ensure_ascii=False, indent='\t')

print('DONE timeline=%d vectorstr=%d' % (counts['timeline'], counts['vector_str']))