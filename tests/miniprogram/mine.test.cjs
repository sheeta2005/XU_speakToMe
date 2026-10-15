const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

// 10/15：执行真实页面脚本，模拟微信宿主及接口，覆盖交互失败和重试行为。
function openPage(name, api = {}, token = 'test-token') {
  let page;
  let modal;
  const storage = { token };
  const toasts = [];
  const navigations = [];
  const app = {
    theme: '',
    getTheme() { return this.theme; },
    setTheme(theme) { this.theme = theme; storage.theme = theme; }
  };
  const wx = {
    getStorageSync: (key) => storage[key],
    setStorageSync: (key, value) => { storage[key] = value; },
    showToast: (options) => toasts.push(options.title),
    showModal: (options) => { modal = options; },
    navigateTo: (options) => navigations.push(options.url),
    reLaunch: (options) => navigations.push(options.url)
  };
  const source = fs.readFileSync(path.join(__dirname, '../../miniprogram/pages/mine', name + '.js'), 'utf8');
  vm.runInNewContext(source, {
    getApp: () => app,
    require: () => api,
    wx,
    Page: (options) => {
      page = options;
      page.setData = (data) => Object.assign(page.data, data);
    }
  });
  return { page, app, storage, toasts, navigations, modal: () => modal };
}

test('10/15：个人中心读取最新校区并保存昵称，同步本地用户资料', async () => {
  const original = { id: 1, nickname: '旧昵称', campusId: 2, avatarUrl: 'https://example.com/avatar' };
  let sent;
  const screen = openPage('index', {
    get: async (url) => url === '/auth/me' ? original : [{ id: 2, name: '雁塔校区' }],
    put: async (url, data) => { sent = { url, ...data }; return { ...original, ...data }; }
  });
  await screen.page.onShow();
  assert.equal(screen.page.data.campusName, '雁塔校区');
  screen.page.onNicknameInput({ detail: { value: '  新昵称  ' } });
  await screen.page.saveProfile();
  assert.deepEqual(sent, { url: '/auth/profile', nickname: '新昵称' });
  assert.equal(screen.storage.user.nickname, '新昵称');
  assert.equal(screen.storage.user.avatarUrl, original.avatarUrl);
  assert.equal(screen.page.data.saving, false);
  screen.page.goReviews();
  screen.page.changeCampus();
  assert.deepEqual(screen.navigations, ['/pages/mine/my-reviews', '/pages/auth/campus']);
});

test('10/15：空昵称不提交，保存失败保留草稿并可重试', async () => {
  let count = 0;
  const screen = openPage('index', {
    put: async () => { count++; throw new Error('保存失败'); }
  });
  screen.page.setData({ user: { nickname: '原昵称' }, nickname: '  ' });
  await screen.page.saveProfile();
  assert.equal(count, 0);
  screen.page.setData({ nickname: '新昵称' });
  await screen.page.saveProfile();
  assert.equal(count, 1);
  assert.equal(screen.page.data.nickname, '新昵称');
  assert.equal(screen.page.data.user.nickname, '原昵称');
  assert.equal(screen.page.data.saving, false);
  assert.equal(screen.toasts.at(-1), '保存失败');
});

test('10/15：个人中心加载失败后可以恢复，停用校区不展示错误名称', async () => {
  let fail = true;
  const screen = openPage('index', {
    get: async (url) => {
      if (fail) throw new Error('网络不可用');
      return url === '/auth/me' ? { nickname: '同学', campusId: 99 } : [];
    }
  });
  await screen.page.onShow();
  assert.equal(screen.page.data.error, '网络不可用');
  assert.equal(screen.page.data.loading, false);
  fail = false;
  await screen.page.loadProfile();
  assert.equal(screen.page.data.error, '');
  assert.equal(screen.page.data.campusName, '未选择或校区已停用');
});

test('10/15：主题设置立即生效并交给全局方法保存', () => {
  const screen = openPage('index');
  screen.page.changeTheme({ detail: { value: '2' } });
  assert.equal(screen.page.data.theme, 'dark');
  assert.equal(screen.storage.theme, 'dark');
  screen.page.changeTheme({ detail: { value: '0' } });
  assert.equal(screen.page.data.theme, '');
});

test('10/15：未登录访问两个页面均跳转登录，且不请求私有数据', () => {
  for (const name of ['index', 'my-reviews']) {
    const screen = openPage(name, {}, '');
    screen.page.onShow();
    assert.deepEqual(screen.navigations, ['/pages/auth/login']);
  }
});

test('10/15：分页失败保留列表与页码，重试仍请求失败页且不重复数据', async () => {
  const urls = [];
  let fail = true;
  const screen = openPage('my-reviews', {
    get: async (url) => {
      urls.push(url);
      if (url.includes('page=1&')) return { list: [{ id: 1, auditStatus: 3 }], total: 2 };
      if (fail) throw new Error('加载失败');
      return { list: [{ id: 2, auditStatus: 1 }], total: 2 };
    }
  });
  await screen.page.onShow();
  assert.equal(screen.page.data.reviews[0].auditLabel, '人工复审中');
  await screen.page.onReachBottom();
  assert.equal(screen.page.data.page, 1);
  assert.equal(screen.page.data.reviews.length, 1);
  fail = false;
  await screen.page.retry();
  assert.equal(screen.page.data.page, 2);
  assert.equal(screen.page.data.reviews.length, 2);
  assert.equal(screen.page.data.reviews[1].id, 2);
  assert.equal(screen.page.data.hasMore, false);
  assert.equal(urls[1], urls[2]);
});

test('10/15：加载期间不重复请求，空列表可正常结束分页', async () => {
  let complete;
  let calls = 0;
  const screen = openPage('my-reviews', {
    get: () => { calls++; return new Promise((resolve) => { complete = resolve; }); }
  });
  const pending = screen.page.onShow();
  screen.page.loadReviews(true);
  assert.equal(calls, 1);
  complete({ list: [], total: 0 });
  await pending;
  assert.equal(screen.page.data.hasMore, false);
  assert.equal(screen.page.data.loading, false);
});

test('10/15：取消删除不发请求，确认删除后从第一页重新加载', async () => {
  let deleted;
  let loaded;
  const screen = openPage('my-reviews', {
    del: async (url) => { deleted = url; },
    get: async (url) => { loaded = url; return { list: [], total: 0 }; }
  });
  screen.page.setData({ reviews: [{ id: 8 }], page: 3, total: 21 });
  screen.page.remove({ currentTarget: { dataset: { id: 8 } } });
  await screen.modal().success({ confirm: false });
  assert.equal(deleted, undefined);
  screen.page.remove({ currentTarget: { dataset: { id: 8 } } });
  await screen.modal().success({ confirm: true });
  assert.equal(deleted, '/reviews/8');
  assert.equal(loaded, '/reviews/mine?page=1&size=10');
  assert.equal(screen.page.data.reviews.length, 0);
  assert.equal(screen.page.data.deletingId, null);
});

test('10/15：删除失败保留现有点评，解除按钮忙碌状态', async () => {
  const screen = openPage('my-reviews', {
    del: async () => { throw new Error('删除失败'); }
  });
  screen.page.setData({ reviews: [{ id: 8 }], page: 1 });
  screen.page.remove({ currentTarget: { dataset: { id: 8 } } });
  await screen.modal().success({ confirm: true });
  assert.equal(screen.page.data.reviews[0].id, 8);
  assert.equal(screen.page.data.deletingId, null);
  assert.equal(screen.toasts.at(-1), '删除失败');
});
