const app = getApp();
const { get, put } = require('../../utils/request');

Page({
  data: {
    theme: '',
    campuses: [],
    selected: null
  },

  onShow() {
    this.setData({ theme: app.getTheme() });
  },

  onLoad() {
    get('/campuses')
      .then((list) => this.setData({ campuses: list }))
      .catch((err) => wx.showToast({ title: err.message, icon: 'none' }));
  },

  /** 选择校区并写回后端 */
  select(e) {
    const campusId = Number(e.currentTarget.dataset.id);
    if (this.data.selected === campusId) return;
    this.setData({ selected: campusId });
    put('/auth/campus', { campusId })
      .then(() => {
        const user = wx.getStorageSync('user') || {};
        user.campusId = campusId;
        wx.setStorageSync('user', user);
        wx.switchTab({ url: '/pages/site/index' });
      })
      .catch((err) => {
        this.setData({ selected: null });
        wx.showToast({ title: err.message, icon: 'none' });
      });
  }
});
