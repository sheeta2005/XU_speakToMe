const app = getApp();
const { get } = require('../../utils/request');

Page({
  data: {
    theme: '',
    campusId: null,
    sites: [],
    loading: true
  },

  onShow() {
    this.setData({ theme: app.getTheme() });
    this.bootstrap();
  },

  /** 登录校验 -> 校区校验 -> 加载地点列表 */
  bootstrap() {
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/auth/login' });
      return;
    }
    get('/auth/me')
      .then((user) => {
        if (!user.campusId) {
          wx.reLaunch({ url: '/pages/auth/campus' });
          return;
        }
        this.setData({ campusId: user.campusId });
        this.loadSites(user.campusId);
      })
      .catch(() => {});
  },

  loadSites(campusId) {
    get('/sites?campusId=' + campusId)
      .then((sites) => this.setData({ sites, loading: false }))
      .catch((err) => {
        this.setData({ loading: false });
        wx.showToast({ title: err.message, icon: 'none' });
      });
  },

  goStalls(e) {
    const { id, name } = e.currentTarget.dataset;
    wx.navigateTo({
      url: '/pages/site/dishes?siteId=' + id + '&siteName=' + encodeURIComponent(name)
    });
  }
});
