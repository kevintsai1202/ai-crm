#!/bin/bash

# Unit 4 安全防護與全域錯誤例外驗收腳本 (macOS / Linux 版本)
# 驗收 Spring Security, JWT 與 RFC 7807 ProblemDetail。

set -e

BASE_URL="http://127.0.0.1:18080/api"

echo "============================================="
echo "  Unit 4 - Spring Security & JWT (macOS/Linux) 驗收開始"
echo "============================================="

# 1. 未攜帶 Token 呼叫保護 API
echo -e "\n[步驟 1] 未攜帶 Token 呼叫保護 API /api/customers..."
STATUS_1=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/customers")
echo "回應狀態碼: $STATUS_1 (預期為 401)"

if [ "$STATUS_1" != "401" ]; then
    echo "安全性漏洞！未登入請求未被攔截拒絕！"
    exit 1
fi
echo "拒絕存取成功！安全保護發揮作用。"

# 2. 測試登入失敗（錯誤密碼）與 RFC 7807 錯誤格式
echo -e "\n[步驟 2] 使用錯誤的密碼進行登入測試..."
RESPONSE_2=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"sales@aurora.local","password":"wrongpassword"}')

STATUS_2=$(echo "$RESPONSE_2" | tail -n1)
BODY_2=$(echo "$RESPONSE_2" | sed '$d')

echo "回應狀態碼: $STATUS_2 (預期為 401)"
if [ "$STATUS_2" != "401" ]; then
    echo "登入校驗漏洞！錯誤密碼未被拒絕！"
    exit 1
fi

echo "驗證 RFC 7807 ProblemDetail 格式..."
if echo "$BODY_2" | grep -q '"title":' && echo "$BODY_2" | grep -q '"detail":' && echo "$BODY_2" | grep -q '"instance":'; then
    echo "全域例外處理器與 ProblemDetail 格式校驗成功！"
else
    echo "錯誤格式不符合 RFC 7807 ProblemDetail 規範！"
    exit 1
fi

# 3. 正常登入取得 Token
echo -e "\n[步驟 3] 執行正常登入取得 Token..."
LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"sales@aurora.local","password":"password123"}')

TOKEN=$(echo "$LOGIN" | grep -o '"token":"[^"]*' | grep -o '[^"]*$')

if [ -z "$TOKEN" ]; then
    echo "正常登入失敗！"
    exit 1
fi
echo "登入成功！Token 取得完成。"

# 4. 攜帶 Token 存取保護 API
echo -e "\n[步驟 4] 攜帶 Bearer Token 重新存取 /api/customers..."
RESPONSE_4=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/customers" -H "Authorization: Bearer $TOKEN")
STATUS_4=$(echo "$RESPONSE_4" | tail -n1)
BODY_4=$(echo "$RESPONSE_4" | sed '$d')

echo "回應狀態碼: $STATUS_4 (預期為 200)"
if [ "$STATUS_4" != "200" ]; then
    echo "攜帶正確 Token 存取失敗！"
    exit 1
fi

TOTAL=$(echo "$BODY_4" | grep -o '"totalElements":[0-9]*' | grep -o '[0-9]*')
echo "資料讀取成功！共查詢到 $TOTAL 筆客戶資料。"

echo -e "\n============================================="
echo "  Unit 4 Spring Security & JWT 驗證順利通過！"
echo "============================================="
