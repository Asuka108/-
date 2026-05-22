import requests

# Test 1: close then AI
print("=== Test 1: Close then AI ===")
r = requests.post("http://127.0.0.1:8000/api/v1/chat/send", json={"message": "转人工", "user_id": 8})
cid = r.json()["conversation_id"]
print("1a. Transfer cid=" + str(cid))

requests.post("http://127.0.0.1:8000/api/v1/agent/transfer-requests/by-conversation/" + str(cid) + "/close")
print("1b. Closed")

r = requests.post("http://127.0.0.1:8000/api/v1/chat/send", json={"message": "蓝牙连不上", "user_id": 8, "conversation_id": cid})
reply = r.json().get("reply", "")
if len(reply) > 10:
    print("1c. OK - AI works after close")
else:
    print("1c. FAIL - reply empty")

# Test 2: normal chat (no transfer) - AI should work
print("\n=== Test 2: Normal AI chat ===")
r = requests.post("http://127.0.0.1:8000/api/v1/chat/send", json={"message": "耳机保修多久", "user_id": 9})
reply = r.json().get("reply", "")
if len(reply) > 10:
    print("2. OK - AI works normally")
else:
    print("2. FAIL - reply empty")
