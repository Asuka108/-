"""
数据库连接与初始化（MySQL）
"""
import os
import pymysql
from dotenv import load_dotenv

load_dotenv()

MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "airpods")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "")
MYSQL_DB = os.getenv("MYSQL_DB", "after_sales_robot")


def get_db():
    """获取MySQL数据库连接"""
    conn = pymysql.connect(
        host=MYSQL_HOST, port=MYSQL_PORT,
        user=MYSQL_USER, password=MYSQL_PASSWORD,
        database=MYSQL_DB, charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )
    return _wrap_conn(conn)


def _wrap_conn(conn):
    """封装连接，统一 execute 接口"""
    class CursorWrapper:
        def __init__(self, cursor):
            self._c = cursor

        def execute(self, sql, params=None):
            return self._c.execute(sql, params or ())

        def fetchone(self):
            return self._c.fetchone()

        def fetchall(self):
            return self._c.fetchall()

        @property
        def lastrowid(self):
            return self._c.lastrowid

        @property
        def rowcount(self):
            return self._c.rowcount

        def close(self):
            self._c.close()

    class ConnWrapper:
        def __init__(self, conn):
            self._conn = conn

        def execute(self, sql, params=None):
            cur = self._conn.cursor()
            cur.execute(sql, params or ())
            return CursorWrapper(cur)

        def cursor(self):
            return CursorWrapper(self._conn.cursor())

        def commit(self):
            self._conn.commit()

        def close(self):
            self._conn.close()

    return ConnWrapper(conn)


def init_db():
    """初始化数据库表"""
    conn = get_db()
    for stmt in [
        """CREATE TABLE IF NOT EXISTS users (
            id INT PRIMARY KEY AUTO_INCREMENT,
            username VARCHAR(32) UNIQUE NOT NULL,
            phone VARCHAR(20),
            nickname VARCHAR(64),
            password_hash VARCHAR(128),
            avatar_url VARCHAR(256),
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """CREATE TABLE IF NOT EXISTS conversations (
            id INT PRIMARY KEY AUTO_INCREMENT,
            user_id INT NOT NULL,
            title VARCHAR(128) DEFAULT '新对话',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """CREATE TABLE IF NOT EXISTS messages (
            id INT PRIMARY KEY AUTO_INCREMENT,
            conversation_id INT NOT NULL,
            role VARCHAR(16) NOT NULL,
            content TEXT NOT NULL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """CREATE TABLE IF NOT EXISTS knowledge_base (
            id INT PRIMARY KEY AUTO_INCREMENT,
            category VARCHAR(32) NOT NULL,
            question TEXT NOT NULL,
            answer TEXT NOT NULL,
            keywords VARCHAR(256),
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """CREATE TABLE IF NOT EXISTS user_tokens (
            token VARCHAR(128) PRIMARY KEY,
            user_id INT NOT NULL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """CREATE TABLE IF NOT EXISTS products (
            id INT PRIMARY KEY AUTO_INCREMENT,
            name VARCHAR(128) NOT NULL,
            model VARCHAR(64),
            category VARCHAR(32),
            description TEXT,
            price DECIMAL(10, 2) NOT NULL,
            original_price DECIMAL(10, 2),
            image_url VARCHAR(512),
            images TEXT,
            stock INT DEFAULT 100,
            sales INT DEFAULT 0,
            rating DECIMAL(2, 1) DEFAULT 5.0,
            specs TEXT,
            features TEXT,
            is_on_sale TINYINT DEFAULT 1,
            sort_order INT DEFAULT 0,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """CREATE TABLE IF NOT EXISTS cart_items (
            id INT PRIMARY KEY AUTO_INCREMENT,
            user_id INT NOT NULL DEFAULT 1,
            product_id INT NOT NULL,
            quantity INT NOT NULL DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """CREATE TABLE IF NOT EXISTS orders (
            id INT PRIMARY KEY AUTO_INCREMENT,
            order_no VARCHAR(32) NOT NULL UNIQUE,
            user_id INT NOT NULL DEFAULT 1,
            total_amount DECIMAL(10, 2) NOT NULL,
            status VARCHAR(16) DEFAULT 'pending',
            receiver_name VARCHAR(32),
            receiver_phone VARCHAR(16),
            receiver_address VARCHAR(256),
            remark TEXT,
            paid_at DATETIME,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """CREATE TABLE IF NOT EXISTS order_items (
            id INT PRIMARY KEY AUTO_INCREMENT,
            order_id INT NOT NULL,
            product_id INT NOT NULL,
            product_name VARCHAR(128),
            product_price DECIMAL(10, 2),
            product_image VARCHAR(512),
            quantity INT NOT NULL DEFAULT 1,
            FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """CREATE TABLE IF NOT EXISTS login_logs (
            id INT PRIMARY KEY AUTO_INCREMENT,
            user_id INT,
            login_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            ip_address VARCHAR(64),
            user_agent VARCHAR(256),
            login_status VARCHAR(16) DEFAULT 'success',
            FOREIGN KEY (user_id) REFERENCES users(id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """CREATE TABLE IF NOT EXISTS user_feedback (
            id INT PRIMARY KEY AUTO_INCREMENT,
            user_id INT NOT NULL,
            conversation_id INT,
            rating INT,
            content TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id),
            FOREIGN KEY (conversation_id) REFERENCES conversations(id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """CREATE TABLE IF NOT EXISTS agents (
            id INT PRIMARY KEY AUTO_INCREMENT,
            username VARCHAR(32) UNIQUE NOT NULL,
            password_hash VARCHAR(128) NOT NULL,
            nickname VARCHAR(64),
            status VARCHAR(20) DEFAULT 'offline',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """CREATE TABLE IF NOT EXISTS transfer_requests (
            id INT PRIMARY KEY AUTO_INCREMENT,
            conversation_id INT NOT NULL,
            user_id INT NOT NULL,
            status VARCHAR(20) DEFAULT 'pending',
            agent_id INT,
            reason VARCHAR(256),
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            FOREIGN KEY (conversation_id) REFERENCES conversations(id),
            FOREIGN KEY (user_id) REFERENCES users(id),
            FOREIGN KEY (agent_id) REFERENCES agents(id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """CREATE TABLE IF NOT EXISTS admin_users (
            id INT PRIMARY KEY AUTO_INCREMENT,
            username VARCHAR(32) UNIQUE NOT NULL,
            password_hash VARCHAR(128) NOT NULL,
            nickname VARCHAR(64),
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
    ]:
        conn.execute(stmt)
    conn.commit()

    # 为已有的 products 表补充新增字段（兼容旧数据库）
    alter_cols = [
        "ADD COLUMN original_price DECIMAL(10, 2) AFTER price",
        "ADD COLUMN images TEXT AFTER image_url",
        "ADD COLUMN stock INT DEFAULT 100 AFTER images",
        "ADD COLUMN sales INT DEFAULT 0 AFTER stock",
        "ADD COLUMN rating DECIMAL(2, 1) DEFAULT 5.0 AFTER sales",
        "ADD COLUMN specs TEXT AFTER rating",
        "ADD COLUMN features TEXT AFTER specs",
        "ADD COLUMN is_on_sale TINYINT DEFAULT 1 AFTER features",
        "ADD COLUMN sort_order INT DEFAULT 0 AFTER is_on_sale",
    ]
    for col_sql in alter_cols:
        try:
            conn.execute(f"ALTER TABLE products {col_sql}")
        except Exception:
            pass  # 字段已存在则忽略
    conn.commit()

    # 插入默认管理员账号（如果不存在）
    import hashlib
    default_pwd_hash = hashlib.sha256("pineapple2026".encode()).hexdigest()
    try:
        conn.execute(
            "INSERT IGNORE INTO admin_users (username, password_hash, nickname) VALUES (%s, %s, %s)",
            ("admin", default_pwd_hash, "管理员"),
        )
        conn.commit()
    except Exception:
        pass

    conn.close()
