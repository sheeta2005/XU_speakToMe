const app = getApp();
const { post } = require('../../utils/request');

Page({
  data: {
    theme: '',
    loading: false
  },

  onShow() {
    this.setData({ theme: app.getTheme() });
  },

  /** wx.login -> 后端 code2session -> 存 token -> 按需跳转 */
  onLogin() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    wx.login({
      success: (res) => {
        post('/auth/login', { code: res.code })
          .then((data) => {
            wx.setStorageSync('token', data.token);
            wx.setStorageSync('user', data.user);
            if (data.needCampus) {
              wx.navigateTo({ url: '/pages/auth/campus' });
            } else {
              wx.switchTab({ url: '/pages/site/index' });
            }
          })
          .catch((err) => wx.showToast({ title: err.message, icon: 'none' }))
          .finally(() => this.setData({ loading: false }));
      },
      fail: () => {
        wx.showToast({ title: '微信登录失败', icon: 'none' });
        this.setData({ loading: false });
      }
    });
  }
});
