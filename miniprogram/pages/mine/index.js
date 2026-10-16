const app = getApp();
const { get, put } = require('../../utils/request');

Page({
  data: {
    theme: '',
    user: null,
    nickname: '',
    campusName: '',
    loading: false,
    saving: false,
    error: '',
    themeOptions: ['跟随系统', '日间模式', '夜间模式'],
    themeIndex: 0
  },

  onShow() {
    const theme = app.getTheme();
    this.setData({ theme, themeIndex: ['', 'light', 'dark'].indexOf(theme) });
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/auth/login' });
      return;
    }
    return this.loadProfile();
  },

  // 10/15：每次进入个人中心读取最新资料，校区修改后不使用旧缓存展示。
  loadProfile() {
    if (this.data.loading || this.data.saving) return;
    this.setData({ loading: true, error: '' });
    return Promise.all([get('/auth/me'), get('/campuses')])
      .then(([user, campuses]) => {
        const campus = campuses.find((item) => item.id === user.campusId);
        wx.setStorageSync('user', user);
        this.setData({
          user,
          nickname: user.nickname || '',
          campusName: campus ? campus.name : '未选择或校区已停用',
          loading: false
        });
      })
      .catch((err) => this.setData({ loading: false, error: err.message }));
  },

  onNicknameInput(e) {
    this.setData({ nickname: e.detail.value });
  },

  // 10/15：复用现有资料接口保存昵称，不改动已有头像地址。
  saveProfile() {
    if (!this.data.user || this.data.loading || this.data.saving) return;
    const nickname = this.data.nickname.trim();
    if (!nickname || nickname.length > 20) {
      wx.showToast({ title: '昵称需为 1-20 个字符', icon: 'none' });
      return;
    }
    this.setData({ saving: true });
    return put('/auth/profile', { nickname })
      .then((user) => {
        wx.setStorageSync('user', user);
        this.setData({ user, nickname: user.nickname });
        wx.showToast({ title: '资料已保存', icon: 'success' });
      })
      .catch((err) => wx.showToast({ title: err.message, icon: 'none' }))
      .finally(() => this.setData({ saving: false }));
  },

  goReviews() {
    wx.navigateTo({ url: '/pages/mine/my-reviews' });
  },

  // 10/16：入口随最新角色显示，管理页进入后还会重新向服务端核验。
  goAdmin() {
    if (this.data.user && this.data.user.role === 1) wx.navigateTo({ url: '/pages/admin/index' });
  },

  changeCampus() {
    wx.navigateTo({ url: '/pages/auth/campus' });
  },

  // 10/15：复用全局三态主题设置并立即刷新当前页面。
  changeTheme(e) {
    const themeIndex = Number(e.detail.value);
    const theme = ['', 'light', 'dark'][themeIndex];
    app.setTheme(theme);
    this.setData({ theme, themeIndex });
  }
});
