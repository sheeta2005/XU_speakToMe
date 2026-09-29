/**
 * 统一请求封装：baseURL 注入、token 自动携带、错误码统一拦截。
 * code=10001/10002 登录失效 -> 清除 token 并跳登录页。
 */
const request = (method, path, data) => new Promise((resolve, reject) => {
  wx.request({
    url: getApp().globalData.baseUrl + path,
    method,
    data,
    header: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer ' + (wx.getStorageSync('token') || '')
    },
    success: (res) => {
      const body = res.data;
      if (body && body.code === 0) {
        resolve(body.data);
      } else if (body && (body.code === 10001 || body.code === 10002)) {
        wx.removeStorageSync('token');
        wx.removeStorageSync('user');
        wx.reLaunch({ url: '/pages/auth/login' });
        reject(new Error(body.msg || '请先登录'));
      } else {
        reject(new Error((body && body.msg) || '网络异常，请稍后重试'));
      }
    },
    fail: () => reject(new Error('网络异常，请稍后重试'))
  });
});

module.exports = {
  get: (path) => request('GET', path),
  post: (path, data) => request('POST', path, data),
  put: (path, data) => request('PUT', path, data),
  del: (path) => request('DELETE', path)
};
