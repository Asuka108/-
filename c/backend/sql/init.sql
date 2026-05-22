-- 菠萝耳机售后机器人 数据库初始化脚本（MySQL）

CREATE DATABASE IF NOT EXISTS after_sales_robot;
USE after_sales_robot;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(32) UNIQUE NOT NULL COMMENT '用户名（登录用）',
    phone VARCHAR(20) COMMENT '手机号',
    nickname VARCHAR(64) COMMENT '昵称',
    password_hash VARCHAR(128) COMMENT '密码哈希值',
    avatar_url VARCHAR(256) COMMENT '头像URL',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 对话表
CREATE TABLE IF NOT EXISTS conversations (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL COMMENT '所属用户ID',
    title VARCHAR(128) DEFAULT '新对话' COMMENT '对话标题',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话表';

-- 消息表
CREATE TABLE IF NOT EXISTS messages (
    id INT PRIMARY KEY AUTO_INCREMENT,
    conversation_id INT NOT NULL COMMENT '所属对话ID',
    role VARCHAR(16) NOT NULL COMMENT '角色：user或assistant',
    source VARCHAR(16) NOT NULL DEFAULT 'ai' COMMENT '来源：ai或human',
    content TEXT NOT NULL COMMENT '消息内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- 知识库表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id INT PRIMARY KEY AUTO_INCREMENT,
    category VARCHAR(32) NOT NULL COMMENT '分类',
    question TEXT NOT NULL COMMENT '常见问题',
    answer TEXT NOT NULL COMMENT '标准回答',
    keywords VARCHAR(256) COMMENT '检索关键词（逗号分隔）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- 产品信息表
CREATE TABLE IF NOT EXISTS products (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL COMMENT '产品名称',
    model VARCHAR(32) COMMENT '产品型号',
    category VARCHAR(32) COMMENT '产品分类',
    description TEXT COMMENT '产品描述',
    price DECIMAL(10, 2) COMMENT '价格',
    image_url VARCHAR(256) COMMENT '产品图片URL',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品信息表';

-- 登录日志表
CREATE TABLE IF NOT EXISTS login_logs (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT COMMENT '用户ID',
    login_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    ip_address VARCHAR(64) COMMENT '登录IP',
    user_agent VARCHAR(256) COMMENT '客户端UA',
    login_status VARCHAR(16) DEFAULT 'success' COMMENT '登录状态',
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

-- 用户反馈表
CREATE TABLE IF NOT EXISTS user_feedback (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL COMMENT '用户ID',
    conversation_id INT COMMENT '关联对话ID',
    rating INT COMMENT '评分(1-5)',
    content TEXT COMMENT '反馈内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '反馈时间',
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户反馈表';
