// 个人中心页逻辑
const app = getApp();

Page({
  data: {
    nickname: '',
    userId: ''
  },

  onShow() {
    // 每次显示时刷新用户信息
    const token = app.getToken();
    if (!token) {
      wx.reLaunch({ url: '/pages/login/login' });
      return;
    }
    this.setData({
      nickname: wx.getStorageSync('nickname') || '用户',
      userId: wx.getStorageSync('userId') || ''
    });
  },

  // 退出登录
  handleLogout() {
    wx.showModal({
      title: '确认退出',
      content: '退出后需要重新登录，确定退出吗？',
      confirmText: '确定退出',
      confirmColor: '#FF3B30',
      cancelText: '取消',
      success: (res) => {
        if (res.confirm) {
          app.logout();
        }
      }
    });
  },

  // 跳转对话记录
  goToHistory() {
    wx.navigateTo({ url: '/pages/history/history' });
  },

  // 跳转意见反馈
  goToFeedback() {
    wx.navigateTo({ url: '/pages/admin/feedback' });
  },

  // 关于我们
  showAbout() {
    wx.showModal({
      title: '关于我们',
      content: '菠萝蓝牙耳机AI售后助手 v1.0\n\n智能售后，随时为您解答耳机使用问题。',
      showCancel: false,
      confirmText: '知道了'
    });
  }
});
