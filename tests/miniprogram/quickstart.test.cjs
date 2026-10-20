const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

// 10/20：执行实际应用入口，验证体验版、正式版不能启用演示登录。
function launch(version) {
  let app;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../miniprogram/app.js'), 'utf8'), {
    require: () => ({
      develop: { baseUrl: 'http://local', demo: true },
      trial: { baseUrl: 'https://trial', demo: true },
      release: { baseUrl: 'https://release', demo: true }
    }),
    App: value => { app = value; },
    wx: { getAccountInfoSync: () => ({ miniProgram: { envVersion: version } }), getStorageSync: () => '' }
  });
  app.onLaunch();
  return app;
}

test('10/20：仅开发版启用演示入口，未知环境按正式版处理', () => {
  assert.equal(launch('develop').globalData.demoMode, true);
  for (const version of ['trial', 'release', 'unknown']) {
    assert.equal(launch(version).globalData.demoMode, false);
  }
  assert.equal(launch('unknown').globalData.baseUrl, 'https://release');
});

function loginScreen(demoMode, post) {
  let page;
  const storage = {};
  const routes = [];
  const toasts = [];
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../miniprogram/pages/auth/login.js'), 'utf8'), {
    getApp: () => ({ getTheme: () => '', globalData: { demoMode } }),
    require: () => ({ post }),
    Page: value => { page = value; page.setData = data => Object.assign(page.data, data); },
    wx: {
      setStorageSync: (key, value) => { storage[key] = value; },
      navigateTo: ({ url }) => routes.push(url),
      switchTab: ({ url }) => routes.push(url),
      showToast: ({ title }) => toasts.push(title)
    }
  });
  page.onShow();
  return { page, storage, routes, toasts };
}
const click = code => ({ currentTarget: { dataset: { code } } });

test('10/20：演示管理员登录缓存身份，首次登录进入校区选择', async () => {
  const screen = loginScreen(true, async (url, data) => {
    assert.equal(url, '/auth/login');
    assert.equal(data.code, 'demo-admin');
    return { token: 'token', user: { role: 1 }, needCampus: true };
  });
  await screen.page.onDemoLogin(click('demo-admin'));
  assert.equal(screen.storage.user.role, 1);
  assert.equal(screen.storage.token, 'token');
  assert.deepEqual(screen.routes, ['/pages/auth/campus']);
  assert.equal(screen.page.data.loading, false);
});

test('10/20：正式环境与非法身份不能发送演示请求', async () => {
  let calls = 0;
  const post = async () => { calls++; };
  await loginScreen(false, post).page.onDemoLogin(click('demo-admin'));
  await loginScreen(true, post).page.onDemoLogin(click('other'));
  assert.equal(calls, 0);
});

test('10/20：演示登录防重复，失败后允许重试', async () => {
  let reject;
  let calls = 0;
  const screen = loginScreen(true, () => { calls++; return new Promise((_, fail) => { reject = fail; }); });
  const pending = screen.page.onDemoLogin(click('demo-user'));
  await screen.page.onDemoLogin(click('demo-user'));
  assert.equal(calls, 1);
  reject(new Error('服务尚未就绪'));
  await pending;
  assert.equal(screen.page.data.loading, false);
  assert.deepEqual(screen.toasts, ['服务尚未就绪']);
  assert.equal(screen.storage.token, undefined);
});
