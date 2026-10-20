/**
 * 全局应用入口：主题管理 + 登录态缓存。
 * 手动切换主题：App.setTheme('light' | 'dark' | '')，'' 表示跟随系统。
 * 页面在 onShow 中读取 theme 绑定到根节点 data-theme 属性。
 */
const environments = require('./config');

App({
  globalData: {
    theme: '',        // '' 跟随系统 / 'light' 手动日间 / 'dark' 手动夜间
    baseUrl: '',
    demoMode: false
  },

  onLaunch() {
    // 10/20：体验版和正式版即使误配 demo 标记，也不显示固定身份登录。
    const version = wx.getAccountInfoSync().miniProgram.envVersion;
    const config = environments[version] || environments.release;
    this.globalData.baseUrl = config.baseUrl;
    this.globalData.demoMode = version === 'develop' && config.demo === true;
    const theme = wx.getStorageSync('theme') || '';
    this.globalData.theme = theme;
  },

  /** 读取当前主题（'' 表示跟随系统） */
  getTheme() {
    return this.globalData.theme;
  },

  /** 设置主题并持久化：theme 传 'light' / 'dark' / '' */
  setTheme(theme) {
    this.globalData.theme = theme;
    wx.setStorageSync('theme', theme);
  }
});
