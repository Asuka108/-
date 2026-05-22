// 菠萝蓝牙耳机售后AI聊天机器人 - 聊天页逻辑
const api = require('../../utils/api.js');

Page({
  data: {
    messages: [],
    inputText: '',
    isLoading: false,
    isError: false,
    scrollToId: '',
    conversationId: null,
    lastFailedMessage: '',
    quickQuestions: [
      'AirPods怎么连接？',
      '左耳没声音怎么办？',
      '保修期多久？',
      '如何辨别真伪？',
      '电池不耐用怎么办？'
    ]
  },

  onLoad(options) {
    // 支持从历史记录跳转，传入 conversationId
    if (options.conversationId) {
      this.setData({ conversationId: parseInt(options.conversationId) });
    }
    this.initConversation();
  },

  // 初始化对话
  async initConversation() {
    const app = getApp();
    const userId = app.getUserId();

    try {
      if (!this.data.conversationId) {
        // 创建新对话 — 后端用查询参数
        const res = await api.post(`/chat/conversations?user_id=${userId}&title=新对话`, {});
        this.setData({ conversationId: res.id || res.conversation_id });
      }

      // 添加欢迎消息
      this.addMessage('ai', '您好！我是菠萝耳机售后AI助手 🎧\n\n我可以帮您：\n• 排查连接和配对问题\n• 解决音质和降噪故障\n• 查询保修政策和维修费用\n• 辨别 AirPods 真伪\n• 对比各型号差异\n\n请直接输入您的问题，或点击下方快捷入口开始吧！');
    } catch (err) {
      console.error('初始化对话失败:', err);
      // 离线模式：仍可显示欢迎消息
      this.addMessage('ai', '您好！我是菠萝耳机售后AI助手，请直接输入您的问题。');
    }
  },

  // 输入框内容变化
  onInputChange(e) {
    this.setData({ inputText: e.detail.value, isError: false });
  },

  // 发送消息
  async sendMessage() {
    const content = this.data.inputText.trim();
    if (!content || this.data.isLoading) return;

    // 添加用户消息
    this.addMessage('user', content);
    this.setData({
      inputText: '',
      isLoading: true,
      isError: false,
      lastFailedMessage: content
    });

    try {
      const app = getApp();
      const res = await api.post('/chat/send', {
        message: content,
        conversation_id: this.data.conversationId,
        user_id: app.getUserId()
      });

      // 添加 AI 回复
      const reply = res.reply || res.data?.reply || '抱歉，我暂时无法回复，请稍后重试。';
      this.addMessage('ai', reply);
      this.setData({ isLoading: false });

    } catch (err) {
      console.error('发送消息失败:', err);
      this.setData({
        isLoading: false,
        isError: true
      });
      this.addMessage('ai', '抱歉，消息发送失败。请检查网络连接后重试。');
    }
  },

  // 添加消息到列表
  addMessage(role, content) {
    const now = new Date();
    const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`;

    const message = {
      id: Date.now(),
      role: role,     // 'user' 或 'ai'
      content: content,
      time: time
    };

    const messages = [...this.data.messages, message];
    this.setData({
      messages: messages,
      scrollToId: 'msg-' + message.id
    });
  },

  // 点击快捷问题
  tapQuickQuestion(e) {
    const question = e.currentTarget.dataset.question;
    this.setData({ inputText: question });
    this.sendMessage();
  },

  // 重试最后一条失败消息
  retryLastMessage() {
    this.setData({
      inputText: this.data.lastFailedMessage,
      isError: false
    });
    // 移除最后一条错误消息
    const messages = this.data.messages.slice(0, -1);
    this.setData({ messages: messages });
    this.sendMessage();
  },

  // 长按消息复制
  copyMessage(e) {
    const content = e.currentTarget.dataset.content;
    wx.setClipboardData({
      data: content,
      success() {
        wx.showToast({ title: '已复制', icon: 'success', duration: 1500 });
      }
    });
  },

  // 跳转个人中心
  goToMine() {
    wx.navigateTo({ url: '/pages/mine/mine' });
  }
});
