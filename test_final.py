import requests

UID = 1

# Step 1: Normal AI chat
r = requests.post("http://127.0.0.1:8000/api/v1/chat/send",
    json={"message": "AirPods保修多久", "user_id": UID})
d = r.json()
cid = d["conversation_id"]
reply = d.get("reply", "")
print("1. AI chat OK, cid=" + str(cid) + ", reply_len=" + str(len(reply)))

# Step 2: Request human transfer
r = requests.post("http://127.0.0.1:8000/api/v1/chat/send",
    json={"message": "转人工", "user_id": UID, "conversation_id": cid})
d = r.json()
print("2. Transfer: " + d.get("reply", "")[:30])

# Step 3: Accept and agent sends message (simulate)
r = requests.get("http://127.0.0.1:8000/api/v1/agent/transfer-requests?status=pending")
reqs = r.json()
if reqs:
    rid = reqs[-1]["id"]
    requests.post("http://127.0.0.1:8000/api/v1/agent/transfer-requests/" + str(rid) + "/accept?agent_id=1")
    print("3. Accepted")

# Step 4: Close transfer (user exits)
requests.post("http://127.0.0.1:8000/api/v1/agent/transfer-requests/by-conversation/" + str(cid) + "/close")
print("4. Closed")

# Step 5: User sends message after exit - AI should work
r = requests.post("http://127.0.0.1:8000/api/v1/chat/send",
    json={"message": "耳机连不上蓝牙怎么办", "user_id": UID, "conversation_id": cid})
reply = r.json().get("reply", "")
if len(reply) > 10:
    print("5. OK - AI works after exit!")
else:
    print("5. FAIL - reply empty or short: " + repr(reply[:80]))
