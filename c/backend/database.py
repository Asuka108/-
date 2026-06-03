"""
数据库连接与初始化（仅支持MySQL）
"""
import os
import pymysql

MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "airpods")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "")
MYSQL_DB = os.getenv("MYSQL_DB", "after_sales_robot")


def get_db():
    """获取MySQL数据库连接（自动将?占位符转为%s）"""
    conn = pymysql.connect(
        host=MYSQL_HOST, port=MYSQL_PORT,
        user=MYSQL_USER, password=MYSQL_PASSWORD,
        database=MYSQL_DB, charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )
    return _wrap_conn(conn)


def _wrap_conn(conn):
    """包装连接，支持?占位符"""
    class CursorWrapper:
        def __init__(self, cursor):
            self._c = cursor

        def execute(self, sql, params=None):
            return self._c.execute(sql.replace("?", "%s"), params or ())

        def executescript(self, sql):
            for stmt in sql.split(";"):
                stmt = stmt.strip()
                if stmt:
                    self._c.execute(stmt.replace("?", "%s"))
            return self

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
            cur.execute(sql.replace("?", "%s"), params or ())
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
    cursor = conn.cursor()
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
            source VARCHAR(16) NOT NULL DEFAULT 'ai',
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
        """CREATE TABLE IF NOT EXISTS products (
            id INT PRIMARY KEY AUTO_INCREMENT,
            name VARCHAR(128) NOT NULL,
            model VARCHAR(32),
            category VARCHAR(32),
            description TEXT,
            price DECIMAL(10, 2),
            image_url VARCHAR(256),
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
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
    ]:
        cursor.execute(stmt)
    # 兼容旧表：为已有messages表添加source列（如果缺失）
    try:
        cursor.execute("ALTER TABLE messages ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'ai'")
    except Exception:
        pass
    conn.commit()
    conn.close()
