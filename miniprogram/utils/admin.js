const { get } = require('./request');

// 10/16：每次进入管理页重新校验角色；真正的授权仍由每个后台接口执行。
function enter(page, load) {
  page.setData({ theme: getApp().getTheme(), allowed: false, loading: true, error: '' });
  if (!wx.getStorageSync('token')) {
    page.setData({ loading: false });
    wx.reLaunch({ url: '/pages/auth/login' });
    return Promise.resolve();
  }
  return get('/auth/me').then((user) => {
    if (user.role !== 1) throw new Error('当前账号没有管理权限');
    page.setData({ allowed: true, loading: false });
    return load.call(page, true);
  }).catch((err) => page.setData({ loading: false, error: err.message }));
}

module.exports = { enter };
