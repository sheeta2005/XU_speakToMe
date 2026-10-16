const { get, post, put } = require('../../utils/request');
const { enter } = require('../../utils/admin');

Page({
  data: {
    theme: '', allowed: false, loading: false, saving: false, error: '', formError: '',
    list: [], page: 0, hasMore: false, draft: null,
    categories: ['政治敏感', '色情内容', '辱骂攻击', '垃圾广告', '学术不端', '其他'],
    levels: ['直接拦截', '人工复审']
  },
  onShow() { return enter(this, this.load); },
  retry() { return enter(this, this.load); },
  // 10/16：词库保留启用和停用记录，编辑可调整分类、等级或恢复启用。
  load(reset) {
    if (!this.data.allowed || this.data.loading || this.data.saving) return;
    const page = reset === true ? 1 : this.data.page + 1;
    this.setData({ loading: true, error: '' });
    return get('/admin/sensitive-words?page=' + page + '&size=20').then((res) => {
      const list = page === 1 ? res.list : this.data.list.concat(res.list);
      this.setData({ list, page, hasMore: list.length < res.total });
    }).catch((err) => this.setData({ error: err.message }))
      .finally(() => this.setData({ loading: false }));
  },
  more() { return this.load(false); },
  onReachBottom() { if (this.data.hasMore && !this.data.error) return this.more(); },
  edit(e) {
    if (this.data.saving || this.data.loading) return;
    const original = this.data.list.find((item) => item.id === Number(e.currentTarget.dataset.id));
    this.setData({ draft: { word: '', category: 1, level: 1, status: 1, ...original }, formError: '' });
    wx.pageScrollTo({ scrollTop: 0, duration: 200 });
  },
  close() { if (!this.data.saving) this.setData({ draft: null }); },
  input(e) { if (!this.data.saving) this.setData({ draft: { ...this.data.draft, word: e.detail.value } }); },
  pick(e) {
    if (!this.data.saving) this.setData({ draft: { ...this.data.draft, [e.currentTarget.dataset.key]: Number(e.detail.value) + 1 } });
  },
  toggle(e) { if (!this.data.saving) this.setData({ draft: { ...this.data.draft, status: e.detail.value ? 1 : 0 } }); },

  // 10/16：仅传递可编辑字段，成功后重新读取词库，失败保留输入。
  save() {
    if (!this.data.allowed || !this.data.draft || this.data.saving || this.data.loading) return;
    const draft = this.data.draft;
    const word = draft.word.trim();
    if (!word || word.length > 64) return this.setData({ formError: '敏感词需为 1-64 个字符' });
    const body = { word, category: draft.category, level: draft.level, status: draft.status };
    this.setData({ saving: true, formError: '' });
    return (draft.id ? put('/admin/sensitive-words/' + draft.id, body) : post('/admin/sensitive-words', body))
      .then(() => {
        this.setData({ saving: false, draft: null });
        wx.showToast({ title: '词库已更新', icon: 'success' });
        return this.load(true);
      }).catch((err) => this.setData({ formError: err.message }))
      .finally(() => this.setData({ saving: false }));
  }
});
