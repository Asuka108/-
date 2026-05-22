// 菠萝蓝牙耳机售后AI聊天机器人 - 小程序入口
App({
  globalData: {
    token: null,
    userId: null,
    baseUrl: 'http://8.137.205.18/api/v1'
  },

  onLaunch() {
    // 检查本地存储的 token
    const token = wx.getStorageSync('token');
    if (token) {
      this.globalData.token = token;
    }
    // 检查本地存储的 userId
    const userId = wx.getStorageSync('userId');
    if (userId) {
      this.globalData.userId = userId;
    }
  },

  // 获取或设置 token
  setToken(token) {
    this.globalData.token = token;
    wx.setStorageSync('token', token);
  },

  getToken() {
    return this.globalData.token || wx.getStorageSync('token');
  },

  // 获取或设置 userId
  setUserId(userId) {
    this.globalData.userId = userId;
    wx.setStorageSync('userId', userId);
  },

  getUserId() {
    return this.globalData.userId || wx.getStorageSync('userId') || 1;
  },

  // 登出：清除 token 并跳转到登录页
  logout() {
    this.globalData.token = null;
    this.globalData.userId = null;
    wx.removeStorageSync('token');
    wx.removeStorageSync('userId');
    wx.reLaunch({ url: '/pages/login/login' });
  }
});
