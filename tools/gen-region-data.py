# -*- coding: utf-8 -*-
"""
从 china-division 的 pca-code.json 生成 uni-app 前端地区数据：
  bookstore-app/common/region-data.js

用法：
  python tools/gen-region-data.py [本地 pca-code.json 路径]
不传路径时自动从 CDN 下载最新数据。

数据源： https://www.npmjs.com/package/china-division
输出结构： { 省: { 市: [区/县...] } }
"""
import argparse
import json
import os
import re
import sys
import tempfile
import urllib.request

SRC_URL = 'https://unpkg.com/china-division@2.7.0/dist/pca-code.json'

# 直辖市下属的技术性分组名称，统一用所属省级名称替代，
# 避免出现 "北京市 > 市辖区 > 东城区" 这类非真实地名；
# 「省/自治区直辖县级行政区划」是官方真实称谓，予以保留。
TECH_GROUP = {'市辖区', '县', '市辖县'}


def load(src):
    if src:
        with open(src, encoding='utf-8') as f:
            return json.load(f)
    print('下载 %s ...' % SRC_URL)
    with urllib.request.urlopen(SRC_URL, timeout=60) as r:
        return json.loads(r.read().decode('utf-8'))


def build(provinces):
    tree = {}
    for p in provinces:
        pname = p['name'].strip()
        cities = {}
        for c in p.get('children') or []:
            cname = c['name'].strip()
            if cname in TECH_GROUP:
                cname = pname
            # 同名.city 合并（如重庆的「市辖区」与「县」都归到「重庆市」下）
            sl = cities.setdefault(cname, [])
            for d in c.get('children') or []:
                dn = d['name'].strip()
                if dn and dn not in sl:
                    sl.append(dn)
            if not sl:
                sl.append(cname)  # 无下级区划的地级单位，第三级复用自身
        if not cities:
            cities[pname] = [pname]
        tree[pname] = cities
    return tree


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('src', nargs='?', default='', help='本地 pca-code.json 路径')
    ap.add_argument('-o', '--out', default=os.path.join(
        os.path.dirname(os.path.abspath(__file__)), '..', 'bookstore-app',
        'common', 'region-data.js'))
    args = ap.parse_args()

    tree = build(load(args.src))
    nc = sum(len(v) for v in tree.values())
    nd = sum(len(x) for v in tree.values() for x in v.values())

    lines = [
        '/**',
        ' * 全国行政区数据（三级）：{ 省: { 市: [区/县...] } }',
        ' * 由 tools/gen-region-data.py 从 china-division@2.7.0 (pca-code) 生成，请勿手工编辑。',
        ' * 覆盖范围：31 个省级行政区（不含港澳台），%d 个地级单位，%d 个区/县。' % (nc, nd),
        ' */',
        'export const REGION_TREE = {',
    ]
    for pi, (pname, cities) in enumerate(tree.items()):
        lines.append('  %s: {' % json.dumps(pname, ensure_ascii=False))
        for ci, (cname, districts) in enumerate(cities.items()):
            tail = ',' if ci < len(cities) - 1 else ''
            items = ', '.join(json.dumps(d, ensure_ascii=False) for d in districts)
            lines.append('    %s: [%s]%s' % (json.dumps(cname, ensure_ascii=False), items, tail))
        lines.append('  }%s' % (',' if pi < len(tree) - 1 else ''))
    lines.append('}')
    lines.append('')
    lines.append('export default REGION_TREE')
    lines.append('')

    out = os.path.abspath(args.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(lines))
    print('生成 %s：省级 %d / 地级 %d / 区县级 %d，%.1f KB'
          % (out, len(tree), nc, nd, os.path.getsize(out) / 1024))


if __name__ == '__main__':
    sys.exit(main())
