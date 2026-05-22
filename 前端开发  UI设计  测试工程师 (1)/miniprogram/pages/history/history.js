// 对话历史页逻辑
const api = require('../../utils/api.js');

Page({
  data: {
    conversations: [],
    isLoading: true
  },

  onShow() {
    this.loadHistory();
  },

  // 下拉刷新
  onPullDownRefresh() {
    this.loadHistory().then(() => {
      wx.stopPullDownRefresh();
    });
  },

  // 加载对话列表
  async loadHistory() {
    this.setData({ isLoading: true });

    try {
      const app = getApp();
      const userId = app.getUserId();
      const res = await api.get('/chat/conversations', { user_id: userId });

      // 后端返回的是数组 [{ id, title, created_at, updated_at }]
      let list = Array.isArray(res) ? res : (res.data || res.conversations || []);

      this.setData({
        conversations: list,
        isLoading: false
      });
    } catch (err) {
      console.error('加载对话记录失败:', err);
      this.setData({ isLoading: false });
      wx.showToast({
        title: '加载失败，请下拉刷新',
        icon: 'none',
        duration: 2000
      });
    }
  },

  // 点击进入对话详情
  openConversation(e) {
    const id = e.currentTarget.dataset.id;
    const title = e.currentTarget.dataset.title;

    wx.showActionSheet({
      itemList: ['继续对话', '取消'],
      success: (res) => {
        if (res.tapIndex === 0) {
          wx.navigateTo({
            url: '/pages/index/index?conversationId=' + id
          });
        }
      }
    });
  }
});
