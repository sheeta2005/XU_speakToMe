const app = getApp();

Page({
  data: { theme: '' },
  onShow() {
    this.setData({ theme: app.getTheme() });
  }
});
