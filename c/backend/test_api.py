"""
AirPods售后AI聊天机器人 - API测试脚本
"""

import requests
import sys

BASE_URL = "http://localhost:8000"


class APITester:
    def __init__(self, base_url: str = BASE_URL):
        self.base_url = base_url
        self.token = None
        self.user_id = None
        self.session = requests.Session()
        self.passed = 0
        self.failed = 0

    def log(self, message: str, success: bool = True):
        status = "OK" if success else "FAIL"
        print(f"  [{status}] {message}")
        if success:
            self.passed += 1
        else:
            self.failed += 1

    def test_health_check(self):
        """1. 健康检查"""
        print("\n1. Health check")
        try:
            r = self.session.get(f"{self.base_url}/health")
            if r.status_code == 200:
                self.log("GET /health - service running")
                return True
            else:
                self.log(f"GET /health - status: {r.status_code}", False)
                return False
        except requests.exceptions.ConnectionError:
            self.log("Cannot connect to server", False)
            return False

    def test_register(self):
        """2. 注册用户"""
        print("\n2. Register user")
        try:
            r = self.session.post(
                f"{self.base_url}/api/v1/auth/register",
                json={"username": "testuser", "phone": "13999999999", "nickname": "TestUser", "password": "123456"}
            )
            if r.status_code == 200:
                data = r.json()
                self.token = data.get("token")
                self.user_id = data.get("user", {}).get("id")
                self.log(f"POST /api/v1/auth/register - user_id: {self.user_id}")
                return True
            elif r.status_code == 400 and "已注册" in r.text:
                self.log("User already registered, trying login instead")
                return self.test_login()
            else:
                self.log(f"Register failed - {r.status_code}: {r.text[:100]}", False)
                return False
        except Exception as e:
            self.log(f"Exception: {e}", False)
            return False

    def test_login(self):
        """3. 登录"""
        print("\n3. Login")
        try:
            r = self.session.post(
                f"{self.base_url}/api/v1/auth/login",
                json={"username": "testuser", "password": "123456"}
            )
            if r.status_code == 200:
                data = r.json()
                self.token = data.get("token")
                self.user_id = data.get("user", {}).get("id")
                self.log(f"POST /api/v1/auth/login - user_id: {self.user_id}")
                return True
            else:
                self.log(f"Login failed - {r.status_code}: {r.text[:100]}", False)
                return False
        except Exception as e:
            self.log(f"Exception: {e}", False)
            return False

    def test_user_info(self):
        """4. 获取用户信息"""
        print("\n4. Get user info")
        if not self.token:
            self.log("No token, skipping", False)
            return False
        try:
            r = self.session.get(
                f"{self.base_url}/api/v1/auth/user/info",
                params={"token": self.token}
            )
            if r.status_code == 200:
                data = r.json()
                self.log(f"GET /api/v1/auth/user/info - nickname: {data.get('nickname')}")
                return True
            else:
                self.log(f"Failed - {r.status_code}", False)
                return False
        except Exception as e:
            self.log(f"Exception: {e}", False)
            return False

    def test_create_conversation(self):
        """5. 创建对话"""
        print("\n5. Create conversation")
        if not self.user_id:
            self.log("No user_id, skipping", False)
            return None
        try:
            r = self.session.post(
                f"{self.base_url}/api/v1/chat/conversations",
                params={"user_id": self.user_id, "title": "TestConversation"}
            )
            if r.status_code == 200:
                data = r.json()
                cid = data.get("id")
                self.log(f"POST /api/v1/chat/conversations - id: {cid}")
                return cid
            else:
                self.log(f"Failed - {r.status_code}: {r.text[:100]}", False)
                return None
        except Exception as e:
            self.log(f"Exception: {e}", False)
            return None

    def test_send_message(self, conversation_id: int):
        """6. 发送消息测试AI回复"""
        print("\n6. Send message (AI chat)")
        if not conversation_id:
            self.log("No conversation_id, skipping", False)
            return

        test_msgs = [
            "AirPods怎么连接iPhone？",
            "电池能用多久？",
            "如何开启降噪模式？"
        ]
        for msg in test_msgs:
            try:
                r = self.session.post(
                    f"{self.base_url}/api/v1/chat/send",
                    json={
                        "message": msg,
                        "conversation_id": conversation_id,
                        "user_id": self.user_id
                    }
                )
                if r.status_code == 200:
                    data = r.json()
                    reply = data.get("reply", "")[:60]
                    self.log(f"Q: {msg[:20]}... -> A: {reply}...")
                else:
                    self.log(f"Failed - {r.status_code}: {r.text[:100]}", False)
            except Exception as e:
                self.log(f"Exception: {e}", False)

    def test_chat_history(self, conversation_id: int):
        """7. 获取对话历史"""
        print("\n7. Get chat history")
        if not conversation_id:
            self.log("No conversation_id, skipping", False)
            return False
        try:
            r = self.session.get(
                f"{self.base_url}/api/v1/chat/history/{conversation_id}"
            )
            if r.status_code == 200:
                data = r.json()
                count = len(data)
                self.log(f"GET /api/v1/chat/history/{conversation_id} - {count} messages")
                return True
            else:
                self.log(f"Failed - {r.status_code}", False)
                return False
        except Exception as e:
            self.log(f"Exception: {e}", False)
            return False

    def test_conversation_list(self):
        """8. 获取对话列表"""
        print("\n8. List conversations")
        if not self.user_id:
            self.log("No user_id, skipping", False)
            return False
        try:
            r = self.session.get(
                f"{self.base_url}/api/v1/chat/conversations",
                params={"user_id": self.user_id}
            )
            if r.status_code == 200:
                data = r.json()
                self.log(f"GET /api/v1/chat/conversations - {len(data)} conversations")
                return True
            else:
                self.log(f"Failed - {r.status_code}", False)
                return False
        except Exception as e:
            self.log(f"Exception: {e}", False)
            return False

    def test_knowledge_search(self):
        """9. 知识库搜索"""
        print("\n9. Knowledge base search")
        queries = ["配对", "电池", "降噪", "维修"]
        all_ok = True
        for q in queries:
            try:
                r = self.session.get(
                    f"{self.base_url}/api/v1/knowledge/search",
                    params={"q": q}
                )
                if r.status_code == 200:
                    data = r.json()
                    count = len(data.get("results", []))
                    self.log(f"Search '{q}' - {count} results")
                else:
                    self.log(f"Search '{q}' failed - {r.status_code}", False)
                    all_ok = False
            except Exception as e:
                self.log(f"Search '{q}' exception: {e}", False)
                all_ok = False
        return all_ok

    def test_knowledge_items(self):
        """10. 知识库列表"""
        print("\n10. Knowledge base items")
        try:
            r = self.session.get(f"{self.base_url}/api/v1/knowledge/items")
            if r.status_code == 200:
                data = r.json()
                self.log(f"GET /api/v1/knowledge/items - {len(data)} items")
                return True
            else:
                self.log(f"Failed - {r.status_code}", False)
                return False
        except Exception as e:
            self.log(f"Exception: {e}", False)
            return False

    def test_swagger_docs(self):
        """11. Swagger文档"""
        print("\n11. Swagger docs")
        try:
            r = self.session.get(f"{self.base_url}/docs")
            if r.status_code == 200:
                self.log("GET /docs - accessible")
                return True
            else:
                self.log(f"Failed - {r.status_code}", False)
                return False
        except Exception as e:
            self.log(f"Exception: {e}", False)
            return False

    def run_all_tests(self):
        print("=" * 60)
        print("AirPods After-Sales AI Chatbot - API Test")
        print("=" * 60)

        # 1. Health check
        if not self.test_health_check():
            print("\n[ERROR] Server not running. Start: cd backend && uvicorn main:app --reload")
            return

        # 2. Register / Login
        self.test_register()

        # 3. User info
        self.test_user_info()

        # 4. Create conversation
        conv_id = self.test_create_conversation()

        # 5. Send messages
        if conv_id:
            self.test_send_message(conv_id)

        # 6. Chat history
        if conv_id:
            self.test_chat_history(conv_id)

        # 7. Conversation list
        self.test_conversation_list()

        # 8. Knowledge search
        self.test_knowledge_search()

        # 9. Knowledge items
        self.test_knowledge_items()

        # 10. Swagger
        self.test_swagger_docs()

        # Summary
        total = self.passed + self.failed
        print(f"\n{'=' * 60}")
        print(f"Result: {self.passed}/{total} passed, {self.failed} failed")
        print("=" * 60)

        if self.failed > 0:
            print("\n[WARNING] Some tests failed, check backend")
            sys.exit(1)
        else:
            print("\n[SUCCESS] All tests passed!")


if __name__ == "__main__":
    APITester().run_all_tests()
