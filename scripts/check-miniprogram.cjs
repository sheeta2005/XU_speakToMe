const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

// 10/21：统一检查注册页面、JSON 和全部小程序脚本，供本机与 CI 使用。
const root = path.resolve(__dirname, '..', 'miniprogram');
function filesIn(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    if (entry.name.startsWith('.') || entry.name === 'miniprogram_npm') return [];
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? filesIn(file) : [file];
  });
}
const files = filesIn(root);
for (const file of files.filter(file => file.endsWith('.json'))) {
  JSON.parse(fs.readFileSync(file, 'utf8'));
}
const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'));
assert.equal(new Set(app.pages).size, app.pages.length, '页面注册不能重复');
for (const page of app.pages) {
  for (const extension of ['js', 'wxml']) {
    assert.ok(fs.existsSync(path.join(root, `${page}.${extension}`)), `缺少页面文件：${page}.${extension}`);
  }
}
for (const item of app.tabBar.list) assert.ok(app.pages.includes(item.pagePath), '标签栏页面未注册');
const scripts = files.filter(file => file.endsWith('.js'));
for (const file of scripts) {
  const result = spawnSync(process.execPath, ['--check', file], { stdio: 'inherit' });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `脚本语法检查失败：${path.relative(root, file)}`);
}
console.log(`小程序静态检查通过：${app.pages.length} 个页面，${scripts.length} 个脚本，全部 JSON。此检查不代替微信编译和真机验收。`);
