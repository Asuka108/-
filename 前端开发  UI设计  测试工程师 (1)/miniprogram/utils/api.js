// API 请求封装
const BASE_URL = 'http://8.137.205.18/api/v1';

/**
 * 统一请求方法
 * @param {string} method  - 请求方法 GET/POST/PUT/DELETE
 * @param {string} url     - 接口路径（不含 BASE_URL）
 * @param {object} data    - 请求体数据（GET 请求时转为 query）
 * @returns {Promise}
 */
function request(method, url, data) {
  return new Promise((resolve, reject) => {
    const app = getApp();
    const token = app ? app.getToken() : wx.getStorageSync('token');

    wx.request({
      method: method,
      url: BASE_URL + url,
      data: data,
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? 'Bearer ' + token : ''
      },
      timeout: 15000, // 15 秒超时
      success(res) {
        if (res.statusCode === 200) {
          resolve(res.data);
        } else if (res.statusCode === 401) {
          // Token 过期，清除本地缓存
          wx.removeStorageSync('token');
          wx.showToast({ title: '登录已过期，请重新打开', icon: 'none' });
          reject(res);
        } else {
          reject(res);
        }
      },
      fail(err) {
        // 网络错误处理
        wx.showToast({
          title: '网络连接失败，请检查网络',
          icon: 'none',
          duration: 2000
        });
        reject(err);
      }
    });
  });
}

// 便捷方法
module.exports = {
  get: (url, data) => request('GET', url, data),
  post: (url, data) => request('POST', url, data),
  put: (url, data) => request('PUT', url, data),
  del: (url, data) => request('DELETE', url, data)
};
