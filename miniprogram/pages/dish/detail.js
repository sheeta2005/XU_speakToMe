const app = getApp();
const { get, post, del } = require('../../utils/request');

Page({
  data: {
    theme: '',
    dishId: null,
    dish: null,
    distribution: [],
    reviews: [],
    page: 1,
    size: 10,
    total: 0,
    hasMore: false,
    showWrite: false,
    rating: 5,
    content: '',
    submitting: false,
    reportId: null,
    reportReasons: ['垃圾广告', '色情内容', '辱骂攻击', '政治敏感', '学术不端', '其他'],
    reportReason: 0,
    reportDetail: '',
    reporting: false
  },

  onLoad(options) {
    this.setData({ dishId: options.dishId });
  },

  onShow() {
    this.setData({ theme: app.getTheme() });
    if (!this.data.dish) this.loadDish();
    this.loadReviews(true);
  },

  /** 菜品详情 + 评分分布（补百分比） */
  loadDish() {
    get('/dishes/' + this.data.dishId).then((dish) => {
      const raw = dish.distribution || {};
      const total = [1, 2, 3, 4, 5].reduce((s, r) => s + (raw[r] || 0), 0);
      const distribution = [1, 2, 3, 4, 5].map((r) => {
        const count = raw[r] || 0;
        return {
          rating: r,
          count,
          percent: total > 0 ? Math.round((count / total) * 100) : 0
        };
      });
      this.setData({ dish, distribution });
    }).catch((err) => wx.showToast({ title: err.message, icon: 'none' }));
  },

  /** 评论分页加载 */
  loadReviews(reset) {
    const page = reset ? 1 : this.data.page + 1;
    get('/dishes/' + this.data.dishId + '/reviews?page=' + page + '&size=' + this.data.size + '&sort=latest')
      .then((res) => {
        const reviews = reset ? res.list : this.data.reviews.concat(res.list);
        this.setData({
          reviews,
          total: res.total,
          page,
          hasMore: reviews.length < res.total
        });
      })
      .catch((err) => wx.showToast({ title: err.message, icon: 'none' }));
  },

  onReachBottom() {
    if (this.data.hasMore) this.loadReviews(false);
  },

  openWrite() {
    this.setData({ showWrite: !this.data.showWrite });
  },

  pickRating(e) {
    this.setData({ rating: Number(e.currentTarget.dataset.r) });
  },

  onInput(e) {
    this.setData({ content: e.detail.value });
  },

  /** 提交点评（后端：本地敏感词 + 微信 msgSecCheck 双检） */
  submit() {
    const content = this.data.content.trim();
    if (content.length < 10 || content.length > 200) {
      wx.showToast({ title: '评论字数需为 10-200 字', icon: 'none' });
      return;
    }
    if (this.data.submitting) return;
    this.setData({ submitting: true });
    post('/reviews', {
      module: 'food',
      targetType: 'dish',
      targetId: Number(this.data.dishId),
      rating: this.data.rating,
      content
    }).then(() => {
      wx.showToast({ title: '点评成功', icon: 'success' });
      this.setData({ showWrite: false, content: '' });
      this.loadDish();
      this.loadReviews(true);
    }).catch((err) => wx.showToast({ title: err.message, icon: 'none' }))
      .finally(() => this.setData({ submitting: false }));
  },

  // 10/16：只为他人已公开的点评打开举报表单，重复举报由后端 24 小时去重。
  openReport(e) {
    if (this.data.reporting) return;
    const review = this.data.reviews.find((item) => item.id === Number(e.currentTarget.dataset.id));
    if (!review || review.deletable || review.auditStatus !== 1) return;
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/auth/login' });
      return;
    }
    this.setData({ reportId: review.id, reportReason: 0, reportDetail: '' });
  },

  pickReportReason(e) { if (!this.data.reporting) this.setData({ reportReason: Number(e.detail.value) }); },
  onReportInput(e) { if (!this.data.reporting) this.setData({ reportDetail: e.detail.value }); },
  cancelReport() { if (!this.data.reporting) this.setData({ reportId: null }); },

  // 10/16：成功后关闭表单；失败保留原因和说明，便于纠正或重试。
  submitReport() {
    if (!this.data.reportId || this.data.reporting) return;
    const reasonDetail = this.data.reportDetail.trim();
    if (reasonDetail.length > 200) {
      wx.showToast({ title: '举报说明不能超过 200 字', icon: 'none' });
      return;
    }
    this.setData({ reporting: true });
    return post('/reports', { reviewId: this.data.reportId, reasonType: this.data.reportReason + 1, reasonDetail })
      .then(() => {
        this.setData({ reportId: null, reportDetail: '' });
        wx.showToast({ title: '举报已提交，等待处理', icon: 'none' });
      }).catch((err) => wx.showToast({ title: err.message, icon: 'none' }))
      .finally(() => this.setData({ reporting: false }));
  },

  /** 删除自己的评论 */
  remove(e) {
    const reviewId = Number(e.currentTarget.dataset.id);
    wx.showModal({
      title: '删除评论',
      content: '删除后不可恢复，确定删除吗？',
      success: (res) => {
        if (!res.confirm) return;
        del('/reviews/' + reviewId)
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' });
            this.loadDish();
            this.loadReviews(true);
          })
          .catch((err) => wx.showToast({ title: err.message, icon: 'none' }));
      }
    });
  }
});
