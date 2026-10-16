const { get, post } = require('../../utils/request');
const { enter } = require('../../utils/admin');

Page({
  data: {
    theme: '', allowed: false, loading: false, working: false, error: '', actionError: '',
    tab: 'reports', statusIndex: 0, list: [], page: 0, hasMore: false,
    reportStatuses: ['待处理', '已下架处理', '已驳回'], reviewStatuses: ['待复审', '已通过', '已拦截'],
    reasons: ['垃圾广告', '色情内容', '辱骂攻击', '政治敏感', '学术不端', '其他'],
    auditLabels: ['待审核', '已通过', '已拦截', '待复审'],
    selected: null, review: null, detailLoading: false, detailError: '', note: ''
  },
  onShow() { return enter(this, this.load); },
  retry() { return enter(this, this.load); },

  // 10/16：两个队列共享分页交互；失败保留页码和已有记录供重试。
  load(reset) {
    if (!this.data.allowed || this.data.loading || this.data.working) return;
    const page = reset === true ? 1 : this.data.page + 1;
    const { tab, statusIndex } = this.data;
    const filter = tab === 'reports' ? 'status=' + statusIndex : 'auditStatus=' + [3, 1, 2][statusIndex];
    this.setData({ loading: true, error: '' });
    return get('/admin/' + tab + '?' + filter + '&page=' + page + '&size=20').then((res) => {
      const list = page === 1 ? res.list : this.data.list.concat(res.list);
      this.setData({ list, page, hasMore: list.length < res.total });
    }).catch((err) => this.setData({ error: err.message }))
      .finally(() => this.setData({ loading: false }));
  },
  more() { return this.load(false); },
  onReachBottom() { if (this.data.hasMore && !this.data.error) return this.more(); },
  changeTab(e) {
    if (this.data.loading || this.data.working || this.data.tab === e.currentTarget.dataset.tab) return;
    this.setData({ tab: e.currentTarget.dataset.tab, statusIndex: 0, list: [], page: 0, selected: null, hasMore: false });
    return this.load(true);
  },
  changeStatus(e) {
    if (this.data.loading || this.data.working) return;
    this.setData({ statusIndex: Number(e.detail.value), list: [], page: 0, selected: null, hasMore: false });
    return this.load(true);
  },

  // 10/16：工单按需读取原文；切换选择后忽略旧请求返回，防止内容串单。
  select(e) {
    if (this.data.working) return;
    const selected = this.data.list.find((item) => item.id === Number(e.currentTarget.dataset.id));
    if (!selected) return;
    this.setData({ selected, review: this.data.tab === 'reviews' ? selected : null,
      note: selected.handleResult || '', actionError: '', detailError: '', detailLoading: false },
    () => wx.pageScrollTo({ selector: '#editor', duration: 200 }));
    if (this.data.tab === 'reports') return this.loadDetail();
  },
  loadDetail() {
    const selected = this.data.selected;
    if (!selected) return;
    const requestId = this._detailRequest = (this._detailRequest || 0) + 1;
    const isCurrent = () => requestId === this._detailRequest && this.data.tab === 'reports'
      && this.data.selected && this.data.selected.id === selected.id;
    this.setData({ detailLoading: true, detailError: '' });
    return get('/admin/reviews/' + selected.reviewId).then((review) => {
      if (isCurrent()) this.setData({ review });
    }).catch((err) => {
      if (isCurrent()) this.setData({ detailError: err.message });
    }).finally(() => {
      if (isCurrent()) this.setData({ detailLoading: false });
    });
  },
  close() { if (!this.data.working) this.setData({ selected: null, detailLoading: false }); },
  onNote(e) { if (!this.data.working) this.setData({ note: e.detail.value }); },
  decide(e) {
    if (this.data.working || !this.data.selected || this.data.detailLoading) return;
    const action = e.currentTarget.dataset.action;
    wx.showModal({ title: '确认处理', content: action === 'block' ? '确认拦截这条点评？公开评分将同步更新。' : '确认提交此处理结果？',
      success: (res) => { if (res.confirm) this.handle(action); } });
  },

  // 10/16：处理成功重载当前分组，失败保留说明和原文，避免重复点击提交。
  handle(action) {
    if (this.data.working || !this.data.allowed || !this.data.selected) return;
    const { tab, selected } = this.data;
    const handleResult = this.data.note.trim();
    if (handleResult.length > 200) return this.setData({ actionError: '处理说明不能超过 200 字' });
    this.setData({ working: true, actionError: '' });
    const path = tab === 'reports' ? '/admin/reports/' + selected.id + '/handle' : '/admin/audit/' + selected.id + '/confirm';
    return post(path, tab === 'reports' ? { action, handleResult } : { action }).then(() => {
      this.setData({ selected: null, working: false });
      wx.showToast({ title: '处理成功', icon: 'success' });
      return this.load(true);
    }).catch((err) => this.setData({ actionError: err.message }))
      .finally(() => this.setData({ working: false }));
  },
  goObjects() { wx.navigateTo({ url: '/pages/admin/objects' }); },
  goWords() { wx.navigateTo({ url: '/pages/admin/words' }); }
});
