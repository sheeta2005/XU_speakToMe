const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

// 10/16：执行页面及权限工具真实脚本，接口与微信宿主使用可控替身。
function openPage(name, api = {}, token = '令牌') {
  let page;
  let modal;
  const toasts = [], navigation = [], storage = { token };
  const wx = {
    getStorageSync: (key) => storage[key],
    showToast: ({ title }) => toasts.push(title),
    reLaunch: ({ url }) => navigation.push(url),
    navigateTo: ({ url }) => navigation.push(url),
    pageScrollTo() {},
    showModal: (options) => { modal = options; }
  };
  const app = { getTheme: () => 'dark' };
  const cache = new Map();
  function load(file) {
    if (file.endsWith('/utils/request.js')) return api;
    if (cache.has(file)) return cache.get(file);
    const module = { exports: {} };
    vm.runInNewContext(fs.readFileSync(file, 'utf8'), {
      module, exports: module.exports, wx, getApp: () => app,
      require: (relative) => load(path.resolve(path.dirname(file), relative + '.js').replaceAll('\\', '/')),
      Page(options) {
        page = options;
        page.setData = (data, callback) => { Object.assign(page.data, data); if (callback) callback(); };
      }
    });
    cache.set(file, module.exports);
    return module.exports;
  }
  load(path.resolve(__dirname, '../../miniprogram/pages', name + '.js').replaceAll('\\', '/'));
  return { page, toasts, navigation, modal: () => modal };
}
const event = (id) => ({ currentTarget: { dataset: { id } } });
const change = (value) => ({ detail: { value } });
const row = (id) => ({ id, content: '评论内容', rating: 5, auditStatus: 1, isDeleted: 0 });

test('10/16：无登录管理页跳转登录，普通用户直达管理页不查询后台数据', async () => {
  for (const name of ['admin/index', 'admin/objects', 'admin/words']) {
    const anonymous = openPage(name, {}, '');
    await anonymous.page.onShow();
    assert.deepEqual(anonymous.navigation, ['/pages/auth/login']);
    const called = [];
    const ordinary = openPage(name, { get: async (url) => { called.push(url); return { role: 0 }; } });
    await ordinary.page.onShow();
    assert.deepEqual(called, ['/auth/me']);
    assert.equal(ordinary.page.data.allowed, false);
    assert.match(ordinary.page.data.error, /没有管理权限/);
  }
});

test('10/16：权限接口失败可重试，管理员进入队列并切换审核状态', async () => {
  let fail = true;
  const called = [];
  const screen = openPage('admin/index', { get: async (url) => {
    called.push(url);
    if (fail) throw new Error('网络失败');
    return url === '/auth/me' ? { role: 1 } : { list: [], total: 0 };
  } });
  await screen.page.onShow();
  assert.equal(screen.page.data.allowed, false);
  fail = false;
  await screen.page.retry();
  assert.equal(screen.page.data.theme, 'dark');
  await screen.page.changeTab({ currentTarget: { dataset: { tab: 'reviews' } } });
  await screen.page.changeStatus(change('2'));
  assert.match(called.at(-1), /auditStatus=2&page=1/);
});

test('10/16：举报失败保留原因说明并可重试，请求期间不重复提交或切换评论', async () => {
  let attempts = 0, sent, resolve;
  const screen = openPage('dish/detail', { post: async (url, body) => {
    attempts++; sent = { url, ...body };
    if (attempts === 1) throw new Error('您已举报过该评论');
    return new Promise((done) => { resolve = done; });
  } });
  screen.page.setData({ reviews: [row(1), row(2)] });
  screen.page.openReport(event(1));
  screen.page.pickReportReason(change('2'));
  screen.page.onReportInput(change('  人身攻击  '));
  await screen.page.submitReport();
  assert.equal(screen.page.data.reportId, 1);
  assert.equal(screen.page.data.reportDetail, '  人身攻击  ');
  assert.equal(screen.toasts.at(-1), '您已举报过该评论');
  const pending = screen.page.submitReport();
  screen.page.openReport(event(2));
  screen.page.cancelReport();
  screen.page.submitReport();
  assert.equal(attempts, 2);
  assert.equal(screen.page.data.reportId, 1);
  resolve(); await pending;
  assert.deepEqual(sent, { url: '/reports', reviewId: 1, reasonType: 3, reasonDetail: '人身攻击' });
  assert.equal(screen.page.data.reportId, null);
  assert.equal(screen.page.data.reporting, false);
});

test('10/16：本人、未公开点评不打开举报表单，未登录举报跳转登录', () => {
  const screen = openPage('dish/detail', {}, '');
  screen.page.setData({ reviews: [{ ...row(1), deletable: true }, { ...row(2), auditStatus: 3 }, row(3)] });
  screen.page.openReport(event(1)); screen.page.openReport(event(2));
  assert.equal(screen.page.data.reportId, null);
  screen.page.openReport(event(3));
  assert.deepEqual(screen.navigation, ['/pages/auth/login']);
});

test('10/16：举报原文请求乱序返回时不会覆盖新工单', async () => {
  const pending = [];
  const screen = openPage('admin/index', { get: () => new Promise((resolve) => pending.push(resolve)) });
  screen.page.setData({ list: [{ id: 1, reviewId: 11 }, { id: 2, reviewId: 22 }] });
  const first = screen.page.select(event(1));
  const second = screen.page.select(event(2));
  pending[1](row(22)); await second;
  pending[0](row(11)); await first;
  assert.equal(screen.page.data.review.id, 22);
  assert.equal(screen.page.data.selected.id, 2);
  assert.equal(screen.page.data.detailLoading, false);
});

test('10/16：队列分页失败不前进页码或丢失已加载记录，重试仍请求同一页', async () => {
  let fail = true;
  const called = [];
  const screen = openPage('admin/index', { get: async (url) => {
    called.push(url);
    if (fail) throw new Error('分页失败');
    return { list: [row(2)], total: 2 };
  } });
  screen.page.setData({ allowed: true, list: [row(1)], page: 1, hasMore: true });
  await screen.page.more();
  assert.equal(screen.page.data.page, 1);
  assert.equal(screen.page.data.list.length, 1);
  fail = false; await screen.page.more();
  assert.equal(called[0], called[1]);
  assert.equal(screen.page.data.page, 2);
  assert.equal(screen.page.data.list.length, 2);
  assert.equal(screen.page.data.hasMore, false);
});

test('10/16：驳回举报需确认，取消不提交，失败保留说明和工单', async () => {
  let count = 0, sent;
  const screen = openPage('admin/index', { post: async (url, body) => { count++; sent = { url, ...body }; throw new Error('处理失败'); } });
  screen.page.setData({ allowed: true, selected: { id: 9, status: 0 }, review: row(1), note: '  无违规  ' });
  screen.page.decide({ currentTarget: { dataset: { action: 'dismiss' } } });
  screen.modal().success({ confirm: false });
  assert.equal(count, 0);
  await screen.page.handle('dismiss');
  assert.deepEqual(sent, { url: '/admin/reports/9/handle', action: 'dismiss', handleResult: '无违规' });
  assert.equal(screen.page.data.selected.id, 9);
  assert.equal(screen.page.data.note, '  无违规  ');
  assert.equal(screen.page.data.actionError, '处理失败');
  assert.equal(screen.page.data.working, false);
});

test('10/16：人工恢复成功清空选择并重新读取当前队列', async () => {
  let sent;
  const screen = openPage('admin/index', {
    post: async (url, body) => { sent = { url, ...body }; },
    get: async () => ({ list: [], total: 0 })
  });
  screen.page.setData({ allowed: true, tab: 'reviews', selected: row(4), list: [row(4)], page: 2 });
  await screen.page.handle('pass');
  assert.deepEqual(sent, { url: '/admin/audit/4/confirm', action: 'pass' });
  assert.equal(screen.page.data.selected, null);
  assert.equal(screen.page.data.list.length, 0);
  assert.equal(screen.page.data.page, 1);
});

const options = { campuses: [{ id: 1, name: '长安', status: 1 }], sites: [{ id: 2, campusId: 1, name: '食堂', status: 1 }], stalls: [{ id: 3, siteId: 2, name: '面食', status: 0 }] };
test('10/16：对象维护按层级选归属，价格可清空且提交中不携带统计字段', async () => {
  let sent;
  const screen = openPage('admin/objects', {
    get: async (url) => url === '/auth/me' ? { role: 1 } : url === '/admin/object-options' ? options : { list: [{ id: 4, stallId: 3, name: '牛肉面', price: 12, avgRating: 5, ratingCount: 10, status: 1, isAvailable: 1, sort: 0 }], total: 1 },
    put: async (url, body) => { sent = JSON.parse(JSON.stringify({ url, ...body })); }
  });
  await screen.page.onShow();
  await screen.page.changeType(change('3'));
  assert.equal(screen.page.data.parents[1].label, '长安 / 食堂 / 面食（已停用）');
  screen.page.edit(event(4));
  screen.page.input({ ...change(''), currentTarget: { dataset: { key: 'price' } } });
  await screen.page.save();
  assert.deepEqual(sent, { url: '/admin/dishes/4', name: '牛肉面', sort: 0, status: 1, stallId: 3, description: '', price: null, isAvailable: 1 });
  assert.equal(screen.page.data.draft, null);
});

test('10/16：对象校验缺失归属和负价格，保存失败保留表单', async () => {
  let attempts = 0;
  const screen = openPage('admin/objects', {
    get: async (url) => url === '/admin/object-options' ? options : { list: [], total: 0 },
    post: async () => { attempts++; throw new Error('保存失败'); }
  });
  screen.page.setData({ allowed: true });
  await screen.page.changeType(change('3'));
  screen.page.edit(event());
  screen.page.input({ ...change('新菜品'), currentTarget: { dataset: { key: 'name' } } });
  await screen.page.save();
  assert.match(screen.page.data.formError, /所属对象/);
  screen.page.pickParent(change('1'));
  screen.page.input({ ...change('-1'), currentTarget: { dataset: { key: 'price' } } });
  await screen.page.save(); assert.equal(attempts, 0);
  screen.page.input({ ...change('8.50'), currentTarget: { dataset: { key: 'price' } } });
  await screen.page.save();
  assert.equal(attempts, 1);
  assert.equal(screen.page.data.draft.name, '新菜品');
  assert.equal(screen.page.data.formError, '保存失败');
  assert.equal(screen.page.data.saving, false);
});

test('10/16：敏感词编辑恢复启用、调整等级，失败不丢草稿且成功后刷新', async () => {
  let fail = true, sent;
  const screen = openPage('admin/words', {
    get: async () => ({ list: [], total: 0 }),
    put: async (url, body) => { sent = { url, ...body }; if (fail) throw new Error('词条已存在'); }
  });
  screen.page.setData({ allowed: true, list: [{ id: 7, word: '代课', category: 5, level: 1, status: 0, source: 'manual' }] });
  screen.page.edit(event(7)); screen.page.toggle(change(true));
  screen.page.pick({ ...change('1'), currentTarget: { dataset: { key: 'level' } } });
  await screen.page.save();
  assert.equal(screen.page.data.formError, '词条已存在');
  assert.equal(screen.page.data.draft.status, 1);
  fail = false; await screen.page.save();
  assert.deepEqual(sent, { url: '/admin/sensitive-words/7', word: '代课', category: 5, level: 2, status: 1 });
  assert.equal(screen.page.data.draft, null);
});
