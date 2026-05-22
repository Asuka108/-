import requests

# Create a conversation with agent messages in history
# First, create transfer
r = requests.post("http://127.0.0.1:8000/api/v1/chat/send", json={"message": "zhuanrengong", "user_id": 5})
d = r.json()
cid = d["conversation_id"]
print("1. Transfer created, cid=" + str(cid))

# Close the transfer
r = requests.post("http://127.0.0.1:8000/api/v1/agent/transfer-requests/by-conversation/" + str(cid) + "/close")
print("2. Closed: " + str(r.json()))

# Now send a regular message - AI should work
r = requests.post("http://127.0.0.1:8000/api/v1/chat/send", json={"message": "AirPods怎么连接", "user_id": 5, "conversation_id": cid})
result = r.json()
reply = result.get("reply", "")
if len(reply) > 10:
    print("3. OK - AI replied: " + reply[:80])
elif "busy" in reply.lower() or "繁忙" in reply:
    print("3. FAIL - still getting busy error: " + reply)
else:
    print("3. UNKNOWN: " + reply[:100])
