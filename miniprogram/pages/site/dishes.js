const app = getApp();
const { get } = require('../../utils/request');

Page({
  data: {
    theme: '',
    siteId: null,
    siteName: '',
    groups: [],
    loading: true
  },

  onLoad(options) {
    this.setData({
      siteId: options.siteId,
      siteName: decodeURIComponent(options.siteName || '')
    });
    wx.setNavigationBarTitle({ title: this.data.siteName });
  },

  onShow() {
    this.setData({ theme: app.getTheme() });
    if (!this.data.groups.length) this.load();
  },

  /** 地点 -> 档口 -> 各档口菜品（并行拉取） */
  load() {
    get('/sites/' + this.data.siteId + '/stalls')
      .then((stalls) => {
        const tasks = stalls.map((stall) =>
          get('/stalls/' + stall.id + '/dishes').then((dishes) => ({ stall, dishes }))
        );
        return Promise.all(tasks);
      })
      .then((groups) => this.setData({ groups, loading: false }))
      .catch((err) => {
        this.setData({ loading: false });
        wx.showToast({ title: err.message, icon: 'none' });
      });
  },

  goDetail(e) {
    wx.navigateTo({
      url: '/pages/dish/detail?dishId=' + e.currentTarget.dataset.id
    });
  }
});
