# 菠萝蓝牙耳机售后AI聊天机器人

基于DeepSeek大模型的菠萝蓝牙耳机智能客服Android应用，为用户提供7x24小时的售后咨询服务。

## 项目简介

本项目是一个面向菠萝蓝牙耳机用户的AI售后客服系统，通过集成DeepSeek大模型和专业知识库，能够自动回答用户关于产品使用、故障排查、保修售后等方面的问题。

### 核心功能

- **智能问答**: 基于DeepSeek大模型的自然语言理解
- **知识库检索**: 覆盖7大类35+条专业知识
- **多轮对话**: 支持上下文记忆的连续对话
- **边界意识**: 自动识别并引导非相关问题
- **快捷提问**: 预设常见问题快速入口
- **对话历史**: 保存和查看历史对话记录

### 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                    Android 前端层                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │  聊天界面   │  │  对话历史   │  │  管理后台   │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                    后端 API 层                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │  对话接口   │  │  用户认证   │  │  知识库API  │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                    数据层                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │   SQLite    │  │  知识库     │  │  日志系统   │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                    AI 服务层                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │  DeepSeek   │  │  提示词工程 │  │  意图识别   │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
└─────────────────────────────────────────────────────────┘
```

## 技术栈

### 后端
- **框架**: Python FastAPI
- **数据库**: SQLite (开发) / MySQL (部署)
- **AI模型**: DeepSeek API
- **分词**: jieba

### 前端
- **平台**: Android (原生)
- **语言**: Kotlin
- **网络**: Retrofit + OkHttp
- **UI**: Material Design

### 部署
- **服务器**: 阿里云/腾讯云轻量应用服务器
- **Web服务器**: Nginx
- **进程管理**: systemd
- **版本控制**: Git + GitHub

## 项目结构

```
pineapple-after-sales-robot/
├── backend/                    # 后端代码
│   ├── main.py                # FastAPI入口
│   ├── routers/               # API路由
│   │   ├── auth.py           # 用户认证
│   │   ├── chat.py           # 对话接口
│   │   └── knowledge.py      # 知识库管理
│   ├── services/              # 业务逻辑
│   │   ├── ai_service.py     # AI服务
│   │   └── kb_service.py     # 知识库服务
│   ├── prompts/               # 提示词
│   │   └── system_prompt.py  # 系统提示词
│   ├── database.py            # 数据库连接
│   ├── models.py              # 数据模型
│   ├── sql/                   # SQL脚本
│   │   ├── init.sql          # 建表语句
│   │   └── seed_kb.sql       # 初始数据
│   ├── knowledge_base.json    # 知识库JSON
│   ├── test_api.py            # API测试脚本
│   └── requirements.txt       # Python依赖
├── Android/                    # Android前端
│   └── app/src/main/
│       ├── java/              # Kotlin代码
│       │   └── com/example/chatscreen/
│       │       ├── MainActivity.kt
│       │       ├── ChatFragment.kt
│       │       ├── ApiService.kt
│       │       └── ...
│       └── res/               # 资源文件
│           ├── layout/        # 布局文件
│           └── values/        # 配置文件
├── deploy/                     # 部署配置
│   ├── nginx.conf             # Nginx配置
│   ├── after-sales-robot.service  # systemd服务
│   └── setup.sh               # 服务器初始化脚本
├── docs/                       # 项目文档
│   ├── API接口文档.md
│   ├── 数据库设计文档.md
│   ├── 提示词版本记录.md
│   └── ...
├── .env.example                # 环境变量示例
├── .gitignore                  # Git忽略规则
└── README.md                   # 项目说明
```

## 快速开始

### 环境要求

- Python 3.11+
- Android Studio (用于编译APK)
- DeepSeek API Key

### 1. 克隆项目

```bash
git clone https://github.com/your-username/pineapple-after-sales-robot.git
cd pineapple-after-sales-robot
```

### 2. 后端设置

```bash
# 创建虚拟环境
python -m venv venv
source venv/bin/activate  # Linux/Mac
# 或 venv\Scripts\activate  # Windows

# 安装依赖
pip install -r backend/requirements.txt

# 配置环境变量
cp .env.example .env
# 编辑 .env 填入你的 DeepSeek API Key

# 初始化数据库
cd backend
python -c "from database import init_db; init_db()"

# 启动后端服务
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

### 3. 测试API

```bash
python backend/test_api.py
```

访问 http://localhost:8000/docs 查看API文档

### 4. 编译Android APK

1. 用Android Studio打开 `Android/` 目录
2. 配置 `ApiService.kt` 中的 `BASE_URL` 为你的后端地址
3. 运行 `Build > Build Bundle(s) / APK(s) > Build APK(s)`
4. 生成的APK在 `Android/app/build/outputs/apk/` 目录

## API接口

### 用户认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/login` | 手机号登录 |
| GET | `/api/v1/user/info` | 获取用户信息 |

### 对话管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/chat/send` | 发送消息 |
| GET | `/api/v1/chat/history/{id}` | 获取对话历史 |
| GET | `/api/v1/chat/conversations` | 获取对话列表 |
| POST | `/api/v1/chat/conversations` | 创建新对话 |

### 知识库

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/knowledge/search?q=` | 搜索知识库 |
| POST | `/api/v1/knowledge/items` | 添加知识条目 |
| PUT | `/api/v1/knowledge/items/{id}` | 更新知识条目 |
| DELETE | `/api/v1/knowledge/items/{id}` | 删除知识条目 |

## 数据库设计

### 核心表

- **users**: 用户表
- **conversations**: 对话表
- **messages**: 消息表
- **knowledge_base**: 知识库表
- **products**: 产品信息表
- **login_logs**: 登录日志表
- **user_feedback**: 用户反馈表

详见 `backend/sql/init.sql`

## 知识库分类

| 分类 | 示例问题 |
|------|----------|
| 配对连接 | AirPods如何配对？连不上手机怎么办？ |
| 充电续航 | 电池能用多久？充电盒怎么充电？ |
| 音质降噪 | 如何开启降噪？音质变差了？ |
| 操作使用 | 手势操作有哪些？如何切换设备？ |
| 保修售后 | 保修期多久？坏了怎么维修？ |
| 真假鉴别 | 如何辨别真假？序列号在哪看？ |
| 产品对比 | AirPods和Pro有什么区别？ |

## 部署

### 云服务器部署

```bash
# 上传代码到服务器
scp -r ./* user@your-server:/var/www/after-sales-robot/

# 登录服务器
ssh user@your-server

# 运行初始化脚本
sudo bash /var/www/after-sales-robot/deploy/setup.sh

# 编辑环境变量
sudo vim /var/www/after-sales-robot/.env

# 启动服务
sudo systemctl start after-sales-robot
```

### 配置HTTPS（可选）

```bash
# 安装certbot
sudo apt install certbot python3-certbot-nginx

# 获取SSL证书
sudo certbot --nginx -d your-domain.com
```

## 团队分工

| 成员 | 角色 | 主要职责 |
|------|------|----------|
| 成员A | 项目经理/后端开发 | 技术选型、后端API、AI集成 |
| 成员B | 前端开发/UI设计 | Android界面、用户体验 |
| 成员C | 文档/数据库/运维 | 数据库设计、部署、文档 |

## 评分项对应

| 评分项 | 分值 | 对应实现 |
|--------|------|----------|
| 项目报告 | 10% | docs/目录下的文档 |
| 功能完整性与实用性 | 20% | 核心功能完整，知识库覆盖全面 |
| 技术实现复杂度与代码质量 | 10% | FastAPI、DeepSeek集成、提示词工程 |
| 用户界面与交互体验 | 10% | Material Design、响应式布局 |
| 项目管理与团队协作 | 10% | Git提交记录、文档规范 |

## 重要提醒

1. **提示词工程是评分重点**: 详细记录设计思路和调优过程（至少迭代3个版本）
2. **边界意识是加分项**: 问非耳机问题时AI应礼貌引导回耳机相关问题
3. **AI回答必须基于知识库**: 不能凭空编造，评委能看出来
4. **报告首页必须包含**: Web应用URL + GitHub地址

## 常见问题

### Q: DeepSeek API调用失败？
A: 检查 `.env` 中的 `DEEPSEEK_API_KEY` 是否正确，账户是否有余额。

### Q: Android连接不上后端？
A: 确保后端已启动，检查 `AndroidManifest.xml` 中的网络安全配置，确认 `ApiService.kt` 中的 `BASE_URL` 正确。

### Q: 数据库初始化失败？
A: 确保有写入权限，检查 `backend/data/` 目录是否存在。

## 许可证

本项目仅用于课程学习，请勿用于商业用途。

## 联系方式

如有问题，请联系团队成员或提交Issue。

---

**Web应用URL**: http://your-domain.com  
**GitHub仓库**: https://github.com/your-username/pineapple-after-sales-robot
