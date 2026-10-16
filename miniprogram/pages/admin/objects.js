const { get, post, put } = require('../../utils/request');
const { enter } = require('../../utils/admin');

// 10/16：四类对象复用同一完整表单，仅列出本批允许维护的业务字段。
const types = [
  { path: 'campuses', name: '校区', max: 32, fields: [] },
  { path: 'sites', name: '地点', max: 64, parent: 'campusId', options: 'campuses', fields: [
    { key: 'location', label: '位置描述', max: 255 }, { key: 'openTime', label: '营业时间', max: 64 }] },
  { path: 'stalls', name: '档口', max: 64, parent: 'siteId', options: 'sites', fields: [
    { key: 'floor', label: '楼层或区域', max: 16 }, { key: 'category', label: '品类', max: 32 }] },
  { path: 'dishes', name: '菜品', max: 64, parent: 'stallId', options: 'stalls', fields: [
    { key: 'description', label: '菜品描述', max: 255 }, { key: 'price', label: '价格（元，可留空）', max: 9 }] }
];

Page({
  data: {
    theme: '', allowed: false, loading: false, saving: false, error: '', formError: '',
    names: types.map((item) => item.name), typeIndex: 0, config: types[0],
    list: [], page: 0, hasMore: false, options: null, parents: [], parentIndex: 0,
    draft: null, fields: [], siteTypes: ['校内食堂', '校外区域', '其他']
  },
  onShow() { return enter(this, this.load); },
  retry() { return enter(this, this.load); },

  // 10/16：列表按页读取；名称选项刷新时包含完整层级和停用提示。
  load(reset) {
    if (!this.data.allowed || this.data.loading || this.data.saving) return;
    const page = reset === true ? 1 : this.data.page + 1;
    this.setData({ loading: true, error: '' });
    return Promise.all([
      get('/admin/' + this.data.config.path + '?page=' + page + '&size=20'),
      reset === true || !this.data.options ? get('/admin/object-options') : Promise.resolve(this.data.options)
    ]).then(([res, options]) => {
      const campuses = new Map(options.campuses.map((item) => [item.id, item.name]));
      const sites = new Map(options.sites.map((item) => [item.id, (campuses.get(item.campusId) || '校区已移除') + ' / ' + item.name]));
      const stalls = new Map(options.stalls.map((item) => [item.id, (sites.get(item.siteId) || '地点已移除') + ' / ' + item.name]));
      const parentNames = [null, campuses, sites, stalls][this.data.typeIndex];
      const rows = res.list.map((item) => ({ ...item, parentName: parentNames ? parentNames.get(item[this.data.config.parent]) || '所属对象已移除' : '' }));
      const list = page === 1 ? rows : this.data.list.concat(rows);
      const parents = this.data.config.options ? [{ id: null, label: '请选择所属对象' }, ...options[this.data.config.options].map((item) => ({
        id: item.id, label: [null, campuses, sites, stalls][this.data.typeIndex].get(item.id) + (item.status === 0 ? '（已停用）' : '')
      }))] : [];
      this.setData({ list, options, parents, page, hasMore: list.length < res.total });
    }).catch((err) => this.setData({ error: err.message }))
      .finally(() => this.setData({ loading: false }));
  },
  more() { return this.load(false); },
  onReachBottom() { if (this.data.hasMore && !this.data.error) return this.more(); },
  changeType(e) {
    if (this.data.loading || this.data.saving) return;
    const typeIndex = Number(e.detail.value);
    this.setData({ typeIndex, config: types[typeIndex], list: [], page: 0, hasMore: false, draft: null });
    return this.load(true);
  },
  edit(e) {
    if (this.data.saving || this.data.loading) return;
    const original = this.data.list.find((item) => item.id === Number(e.currentTarget.dataset.id));
    const draft = { name: '', sort: 0, status: 1, siteType: 1, isAvailable: 1, ...original };
    const parentIndex = Math.max(0, this.data.parents.findIndex((item) => item.id === draft[this.data.config.parent]));
    const fields = this.data.config.fields.map((field) => ({ ...field, value: draft[field.key] == null ? '' : String(draft[field.key]) }));
    this.setData({ draft, fields, parentIndex, formError: '' });
    wx.pageScrollTo({ scrollTop: 0, duration: 200 });
  },
  close() { if (!this.data.saving) this.setData({ draft: null }); },
  input(e) {
    if (this.data.saving) return;
    const key = e.currentTarget.dataset.key;
    const draft = { ...this.data.draft, [key]: e.detail.value };
    this.setData({ draft, fields: this.data.fields.map((field) => field.key === key ? { ...field, value: e.detail.value } : field) });
  },
  pickParent(e) { if (!this.data.saving) this.setData({ parentIndex: Number(e.detail.value) }); },
  pickSiteType(e) { if (!this.data.saving) this.setData({ draft: { ...this.data.draft, siteType: Number(e.detail.value) + 1 } }); },
  toggle(e) { if (!this.data.saving) this.setData({ draft: { ...this.data.draft, [e.currentTarget.dataset.key]: e.detail.value ? 1 : 0 } }); },

  // 10/16：提交字段白名单，不把列表中的评分、时间和显示名称传回后台。
  save() {
    if (!this.data.allowed || !this.data.draft || this.data.saving || this.data.loading) return;
    const { draft, config, parentIndex, parents } = this.data;
    const name = draft.name.trim();
    if (!name || name.length > config.max) return this.setData({ formError: '名称需为 1-' + config.max + ' 个字符' });
    const sort = Number(draft.sort);
    if (!Number.isInteger(sort) || sort < 0 || sort > 255) return this.setData({ formError: '排序需为 0-255 的整数' });
    const body = { name, sort, status: draft.status };
    if (config.parent) {
      if (!parents[parentIndex] || !parents[parentIndex].id) return this.setData({ formError: '请选择所属对象；没有选项时请先新增上级对象' });
      body[config.parent] = parents[parentIndex].id;
    }
    config.fields.forEach((field) => { body[field.key] = draft[field.key] == null ? '' : String(draft[field.key]).trim(); });
    if (config.path === 'sites') body.siteType = draft.siteType;
    if (config.path === 'dishes') {
      const price = body.price;
      if (price && (!/^\d{1,6}(\.\d{1,2})?$/.test(price))) return this.setData({ formError: '价格需为非负金额，最多两位小数' });
      body.price = price === '' ? null : Number(price);
      body.isAvailable = draft.isAvailable;
    }
    this.setData({ saving: true, formError: '' });
    return (draft.id ? put('/admin/' + config.path + '/' + draft.id, body) : post('/admin/' + config.path, body))
      .then(() => {
        this.setData({ saving: false, draft: null });
        wx.showToast({ title: '保存成功', icon: 'success' });
        return this.load(true);
      }).catch((err) => this.setData({ formError: err.message }))
      .finally(() => this.setData({ saving: false }));
  }
});
