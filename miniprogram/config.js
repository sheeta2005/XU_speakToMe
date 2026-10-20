// 10/20：按微信发布环境选择接口；演示入口仅允许开发版显示，正式地址由部署者替换。
module.exports = {
  develop: { baseUrl: 'http://127.0.0.1:8080/api/v1', demo: true },
  trial: { baseUrl: 'https://api.example.com/api/v1', demo: false },
  release: { baseUrl: 'https://api.example.com/api/v1', demo: false }
};
