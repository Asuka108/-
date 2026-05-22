import requests

# 1. User requests transfer
r = requests.post('http://127.0.0.1:8000/api/v1/chat/send', json={'message': '转人工', 'user_id': 2})
conv = r.json()
cid = conv['conversation_id']
print('1. Transfer created, cid:', cid)

# 2. Get transfer request id
r = requests.get('http://127.0.0.1:8000/api/v1/agent/transfer-requests?status=pending')
reqs = r.json()
if reqs:
    rid = reqs[0]['id']
    # Accept
    r = requests.post(f'http://127.0.0.1:8000/api/v1/agent/transfer-requests/{rid}/accept?agent_id=1')
    print('2. Accept:', r.json())

# 3. Close transfer (simulate user exit)
r = requests.post(f'http://127.0.0.1:8000/api/v1/agent/transfer-requests/by-conversation/{cid}/close')
print('3. Close:', r.json())

# 4. User sends message after exit - should get AI reply
r = requests.post('http://127.0.0.1:8000/api/v1/chat/send', json={'message': '耳机连不上怎么办', 'user_id': 2, 'conversation_id': cid})
result = r.json()
reply = result.get('reply', '')
if reply:
    print('4. OK - AI replied:', reply[:80])
else:
    print('4. FAIL - empty reply!')
