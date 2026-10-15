const app = getApp();
const { get, del } = require('../../utils/request');

Page({
  data: {
    theme: '',
    reviews: [],
    page: 0,
    size: 10,
    total: 0,
    hasMore: false,
    loading: false,
    deletingId: null,
    error: ''
  },

  onShow() {
    this.setData({ theme: app.getTheme() });
    if (!wx.getStorageSync('token')) {
      wx.reLaunch({ url: '/pages/auth/login' });
      return;
    }
    if (!this.data.deletingId) return this.loadReviews(true);
  },

  // 10/15：加载本人点评，分页失败时保留已有数据和页码，便于重试。
  loadReviews(reset) {
    if (this.data.loading) return;
    const page = reset ? 1 : this.data.page + 1;
    if (reset) this.setData({ reviews: [], page: 0, total: 0, hasMore: false });
    this.setData({ loading: true, error: '' });
    const auditLabels = { 0: '待审核', 1: '已通过', 2: '未通过', 3: '人工复审中' };
    return get('/reviews/mine?page=' + page + '&size=' + this.data.size)
      .then((res) => {
        const list = res.list.map((review) => ({
          ...review,
          auditLabel: auditLabels[review.auditStatus]
        }));
        const reviews = reset ? list : this.data.reviews.concat(list);
        this.setData({
          reviews, page, total: res.total,
          hasMore: reviews.length < res.total,
          loading: false
        });
      })
      .catch((err) => this.setData({ loading: false, error: err.message }));
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.error && !this.data.deletingId) {
      return this.loadReviews(false);
    }
  },

  retry() {
    return this.loadReviews(this.data.reviews.length === 0);
  },

  // 10/15：删除仍由后端校验归属，确认成功后从第一页重载，避免分页遗漏。
  remove(e) {
    if (this.data.loading || this.data.deletingId) return;
    const reviewId = Number(e.currentTarget.dataset.id);
    wx.showModal({
      title: '删除点评',
      content: '删除后不可恢复，确定删除吗？',
      success: (res) => {
        if (!res.confirm || this.data.loading || this.data.deletingId) return;
        this.setData({ deletingId: reviewId });
        return del('/reviews/' + reviewId)
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' });
            return this.loadReviews(true);
          })
          .catch((err) => wx.showToast({ title: err.message, icon: 'none' }))
          .finally(() => this.setData({ deletingId: null }));
      }
    });
  }
});
