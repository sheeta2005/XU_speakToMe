const app = getApp();
const { get } = require('../../utils/request');

Page({
  data: {
    theme: '',
    days: 7,
    rankings: [],
    loading: false,
    error: ''
  },

  onShow() {
    this.setData({ theme: app.getTheme() });
    this.loadRankings();
  },

  // 10/14：读取后端按 7 天或 30 天统计的热门菜品榜单。
  loadRankings() {
    if (this.data.loading) return;
    const days = this.data.days;
    this.setData({ loading: true, error: '' });
    get('/rankings/hot-dishes?days=' + days + '&limit=20')
      .then((rankings) => this.setData({ rankings: rankings || [], loading: false }))
      .catch((err) => this.setData({
        loading: false,
        error: err.message || '榜单加载失败'
      }));
  },

  changeDays(e) {
    const days = Number(e.currentTarget.dataset.days);
    if (days === this.data.days || this.data.loading) return;
    this.setData({ days, rankings: [] });
    this.loadRankings();
  },

  goDetail(e) {
    wx.navigateTo({
      url: '/pages/dish/detail?dishId=' + e.currentTarget.dataset.id
    });
  }
});
