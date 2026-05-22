"""测试用户名+密码注册登录"""
import subprocess
import time
import requests

proc = subprocess.Popen(
    ["python", "-m", "uvicorn", "main:app", "--host", "127.0.0.1", "--port", "8001"],
    stdout=subprocess.PIPE, stderr=subprocess.PIPE,
)
time.sleep(3)

BASE = "http://127.0.0.1:8001"
passed = 0
failed = 0


def test(name, ok, detail=""):
    global passed, failed
    status = "OK" if ok else "FAIL"
    if ok:
        passed += 1
    else:
        failed += 1
    print("  [{}] {} {}".format(status, name, detail))


print("=" * 50)
print("  Username + Password Login Test")
print("=" * 50)

# 1. Register
print("\n[1] Register")
r = requests.post(f"{BASE}/api/v1/auth/register", json={"username": "testuser", "password": "pass123"})
test("New user register", r.status_code == 200, "status={}".format(r.status_code))
if r.status_code == 200:
    data = r.json()
    token = data.get("token")
    user = data.get("user", {})
    test("Returns token", bool(token))
    test("Returns username", user.get("username") == "testuser")
    test("Returns id", user.get("id") is not None)
    print("    token={} user={}".format(token[:12] if token else None, user))
else:
    token = None
    print("    body={}".format(r.text[:200]))

# 2. Duplicate register
r2 = requests.post(f"{BASE}/api/v1/auth/register", json={"username": "testuser", "password": "pass123"})
test("Duplicate register rejected", r2.status_code == 400)

# 3. Register with nickname
r3 = requests.post(f"{BASE}/api/v1/auth/register", json={"username": "user2", "password": "abc123", "nickname": "NickName"})
test("Register with nickname", r3.status_code == 200)
if r3.status_code == 200:
    test("Nickname stored", r3.json().get("user", {}).get("nickname") == "NickName")

# 4. Login correct
print("\n[2] Login")
r4 = requests.post(f"{BASE}/api/v1/auth/login", json={"username": "testuser", "password": "pass123"})
test("Correct password", r4.status_code == 200)
if r4.status_code == 200:
    login_token = r4.json().get("token")
    test("Login returns token", bool(login_token))

# 5. Login wrong password
r5 = requests.post(f"{BASE}/api/v1/auth/login", json={"username": "testuser", "password": "wrong"})
test("Wrong password -> 401", r5.status_code == 401)

# 6. Login nonexistent
r6 = requests.post(f"{BASE}/api/v1/auth/login", json={"username": "nobody", "password": "abc"})
test("Nonexistent -> 404", r6.status_code == 404)

# 7. Empty username
r7 = requests.post(f"{BASE}/api/v1/auth/register", json={"username": "", "password": "abc"})
test("Empty username -> 422", r7.status_code == 422)

# 8. User info
print("\n[3] User Info")
if token:
    r8 = requests.get(f"{BASE}/api/v1/auth/user/info", params={"token": token})
    test("Get info (query param)", r8.status_code == 200)
    if r8.status_code == 200:
        test("Returns username", r8.json().get("username") == "testuser")

    r9 = requests.get(f"{BASE}/api/v1/auth/user/info", headers={"Authorization": f"Bearer {token}"})
    test("Get info (header)", r9.status_code == 200)

r10 = requests.get(f"{BASE}/api/v1/auth/user/info", params={"token": "bad"})
test("Invalid token -> 401", r10.status_code == 401)

# Summary
total = passed + failed
print("\n" + "=" * 50)
print("  Result: {}/{} passed, {} failed".format(passed, total, failed))
print("=" * 50)

proc.terminate()
