// 管理员后台 - 知识库管理页逻辑
const api = require('../../utils/api.js');
const app = getApp();

Page({
  data: {
    allItems: [],
    filteredList: [],
    categories: ['全部', '配对连接', '充电续航', '音质降噪', '操作使用', '保修售后', '真假鉴别', '产品对比'],
    activeCategory: '全部',
    categoryCount: {},
    searchKeyword: '',

    // 弹窗状态
    showDialog: false,
    editingId: null,
    selectedCategoryIndex: 0,
    formQuestion: '',
    formAnswer: '',
    formKeywords: '',

    // 删除确认
    showDeleteConfirm: false,
    deleteTargetId: null
  },

  onLoad() {
    this.loadKnowledgeList();
  },

  // 加载知识列表
  async loadKnowledgeList() {
    try {
      const res = await api.get('/knowledge/search', { q: '' });
      const items = res.items || res.data || [];
      this.setData({ allItems: items });
      this.applyFilters();
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  // 搜索
  onSearchInput(e) {
    this.setData({ searchKeyword: e.detail.value });
    this.applyFilters();
  },

  // 分类筛选
  filterByCategory(e) {
    const cat = e.currentTarget.dataset.cat;
    this.setData({ activeCategory: cat });
    this.applyFilters();
  },

  // 应用筛选条件
  applyFilters() {
    let list = this.data.allItems;
    const cat = this.data.activeCategory;
    const keyword = this.data.searchKeyword.toLowerCase();

    if (cat !== '全部') {
      list = list.filter(item => item.category === cat);
    }

    if (keyword) {
      list = list.filter(item =>
        item.question.includes(keyword) ||
        item.keywords.some(k => k.includes(keyword))
      );
    }

    // 统计各分类数量
    const counts = {};
    this.data.categories.slice(1).forEach(c => {
      counts[c] = this.data.allItems.filter(i => i.category === c).length;
    });
    counts['全部'] = this.data.allItems.length;

    this.setData({
      filteredList: list,
      categoryCount: counts
    });
  },

  // 打开新增弹窗
  openAddDialog() {
    this.setData({
      showDialog: true,
      editingId: null,
      selectedCategoryIndex: 0,
      formQuestion: '',
      formAnswer: '',
      formKeywords: ''
    });
  },

  // 打开编辑弹窗
  openEditDialog(e) {
    const id = e.currentTarget.dataset.id;
    const item = this.data.allItems.find(i => i.id === id);
    if (!item) return;

    const catIndex = this.data.categories.indexOf(item.category);

    this.setData({
      showDialog: true,
      editingId: id,
      selectedCategoryIndex: catIndex >= 0 ? catIndex : 0,
      formQuestion: item.question,
      formAnswer: item.answer,
      formKeywords: (item.keywords || []).join(',')
    });
  },

  // 关闭弹窗
  closeDialog() {
    this.setData({ showDialog: false });
  },

  // 表单字段变化
  onCategoryChange(e) {
    this.setData({ selectedCategoryIndex: parseInt(e.detail.value) });
  },

  onQuestionInput(e) {
    this.setData({ formQuestion: e.detail.value });
  },

  onAnswerInput(e) {
    this.setData({ formAnswer: e.detail.value });
  },

  onKeywordsInput(e) {
    this.setData({ formKeywords: e.detail.value });
  },

  // 保存知识
  async saveKnowledge() {
    const cat = this.data.categories[this.data.selectedCategoryIndex];
    if (cat === '全部') {
      wx.showToast({ title: '请选择分类', icon: 'none' });
      return;
    }
    if (!this.data.formQuestion.trim()) {
      wx.showToast({ title: '请输入问题', icon: 'none' });
      return;
    }
    if (!this.data.formAnswer.trim()) {
      wx.showToast({ title: '请输入回答', icon: 'none' });
      return;
    }

    const data = {
      category: cat,
      question: this.data.formQuestion.trim(),
      answer: this.data.formAnswer.trim(),
      keywords: this.data.formKeywords.split(',').map(k => k.trim()).filter(k => k)
    };

    try {
      if (this.data.editingId) {
        await api.put(`/knowledge/items/${this.data.editingId}`, data);
        wx.showToast({ title: '修改成功', icon: 'success' });
      } else {
        await api.post('/knowledge/items', data);
        wx.showToast({ title: '新增成功', icon: 'success' });
      }
      this.closeDialog();
      this.loadKnowledgeList();
    } catch (err) {
      wx.showToast({ title: '保存失败', icon: 'none' });
    }
  },

  // 删除确认
  confirmDelete(e) {
    this.setData({
      showDeleteConfirm: true,
      deleteTargetId: e.currentTarget.dataset.id
    });
  },

  cancelDelete() {
    this.setData({ showDeleteConfirm: false, deleteTargetId: null });
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

  // 执行删除
  async doDelete() {
    const id = this.data.deleteTargetId;
    if (!id) return;

    try {
      await api.del(`/knowledge/items/${id}`);
      wx.showToast({ title: '已删除', icon: 'success' });
      this.setData({ showDeleteConfirm: false, deleteTargetId: null });
      this.loadKnowledgeList();
    } catch (err) {
      wx.showToast({ title: '删除失败', icon: 'none' });
    }
  }
});
