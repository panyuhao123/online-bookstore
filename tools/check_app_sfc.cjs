/**
 * 强校验：@vue/compiler-sfc 解析 + 模板编译 + script 语法（node --check 等价的 ESM 解析）
 */
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const sfc = require('D:/workbuddy123/线上书店/bookstore-admin/node_modules/@vue/compiler-sfc');
const compilerDom = require('D:/workbuddy123/线上书店/bookstore-admin/node_modules/@vue/compiler-dom');

const APP = 'D:/workbuddy123/线上书店/bookstore-app';
const NODE = 'C:/Users/86138/.workbuddy/binaries/node/versions/22.22.2-6/node.exe';
const TMP = 'D:/tmp/sfcchk';

fs.rmSync(TMP, { recursive: true, force: true });
fs.mkdirSync(TMP, { recursive: true });

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name);
    const st = fs.statSync(p);
    if (st.isDirectory()) {
      if (['node_modules', 'unpackage', '.git', 'static'].includes(name)) continue;
      walk(p, out);
    } else if (name.endsWith('.vue')) out.push(p);
  }
  return out;
}

function jsFiles(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name);
    const st = fs.statSync(p);
    if (st.isDirectory()) {
      if (['node_modules', 'unpackage', '.git', 'static'].includes(name)) continue;
      jsFiles(p, out);
    } else if (name.endsWith('.js')) out.push(p);
  }
  return out;
}

let ok = 0;
const fails = [];

// ---- 1. SFC ----
for (const file of walk(APP)) {
  const rel = path.relative(APP, file).replace(/\\/g, '/');
  const source = fs.readFileSync(file, 'utf8');
  try {
    const { descriptor, errors } = sfc.parse(source, { filename: file });
    if (errors && errors.length) throw new Error(errors.map(e => e.message).join(' | '));

    // 模板编译（暴露未知指令/表达式语法错误）
    if (descriptor.template && descriptor.template.content.trim()) {
      const r = compilerDom.compile(descriptor.template.content, { onError(e) { throw e; } });
      if (r.errors && r.errors.length) throw new Error(r.errors.map(e => e.message).join(' | '));
    }

    // script 语法检查
    const scriptContent = descriptor.scriptSetup
      ? descriptor.scriptSetup.content
      : (descriptor.script ? descriptor.script.content : '');
    if (!scriptContent.trim()) throw new Error('空 <script>');
    const tmp = path.join(TMP, rel.replace(/[\/]/g, '_') + '.mjs');
    fs.writeFileSync(tmp, scriptContent, 'utf8');
    execFileSync(NODE, ['--check', tmp], { stdio: 'pipe' });

    const styles = (descriptor.styles || []).length;
    ok++;
    console.log(`[OK]   ${rel}  (template=${descriptor.template ? 'y' : 'n'} style=${styles})`);
  } catch (e) {
    fails.push([rel, (e.message || String(e)).split('\n')[0]]);
    console.log(`[FAIL] ${rel} -> ${(e.message || String(e)).split('\n')[0]}`);
  }
}

// ---- 2. 纯 JS 文件 ----
console.log('\n--- .js 文件语法 ---');
for (const file of jsFiles(APP)) {
  const rel = path.relative(APP, file).replace(/\\/g, '/');
  if (rel.includes('unpackage')) continue;
  const tmp = path.join(TMP, rel.replace(/[\/]/g, '_') + '.mjs');
  try {
    fs.writeFileSync(tmp, fs.readFileSync(file, 'utf8'), 'utf8');
    execFileSync(NODE, ['--check', tmp], { stdio: 'pipe' });
    console.log(`[OK]   ${rel}`);
  } catch (e) {
    const msg = (e.stderr ? e.stderr.toString() : e.message).split('\n').filter(Boolean)[0] || '';
    fails.push([rel, msg]);
    console.log(`[FAIL] ${rel} -> ${msg}`);
  }
}

console.log('\n===== SFC 通过 ' + ok + ' / 失败 ' + fails.length + ' =====');
process.exit(fails.length ? 1 : 0);
