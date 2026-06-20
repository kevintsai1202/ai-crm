#!/bin/bash

# Unit 3 資料庫與動態查詢驗收腳本 (macOS / Linux 版本)
# 測試與真實 PostgreSQL 連線，並驗證 CustomerSpecification 的動態篩選功能。

set -e

BASE_URL="http://127.0.0.1:18080/api"

echo "============================================="
echo "  Unit 3 - PostgreSQL & Specification (macOS/Linux) 驗收開始"
echo "============================================="

# 1. 檢查後端健康狀態
echo -e "\n[步驟 1] 檢查後端健康狀態..."
HEALTH=$(curl -s "$BASE_URL/health")
STATUS=$(echo "$HEALTH" | grep -o '"status":"[^"]*' | grep -o '[^"]*$')
TIMESTAMP=$(echo "$HEALTH" | grep -o '"timestamp":"[^"]*' | grep -o '[^"]*$')
echo "後端狀態: $STATUS"
echo "系統時間: $TIMESTAMP"

if [ "$STATUS" != "UP" ]; then
    echo "健康檢查失敗！"
    exit 1
fi

# 2. 登入取得 Token
echo -e "\n[步驟 2] 登入取得驗證 Token..."
LOGIN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"sales@aurora.local","password":"password123"}')

TOKEN=$(echo "$LOGIN" | grep -o '"token":"[^"]*' | grep -o '[^"]*$')

if [ -z "$TOKEN" ]; then
    echo "無法取得 Token，登入失敗！"
    exit 1
fi
echo "登入成功！Token 取得完成。"

# 3. 測試多條件 Specification 動態搜尋
echo -e "\n[步驟 3] 測試動態 Specification 查詢條件..."

# A. 模糊姓名搜尋
echo -e "\n  -> 條件 A: 搜尋名稱包含 '星河' 的客戶"
RESULT_A=$(curl -s -X GET "$BASE_URL/customers?keyword=星河" -H "Authorization: Bearer $TOKEN")
echo "$RESULT_A" | grep -q "星河製造股份有限公司" && echo "     驗證成功: 包含 星河" || (echo "     驗證失敗！" && exit 1)

# B. 產業精確篩選
echo -e "\n  -> 條件 B: 篩選產業為 '物流' 的客戶"
RESULT_B=$(curl -s -X GET "$BASE_URL/customers?industry=物流" -H "Authorization: Bearer $TOKEN")
echo "$RESULT_B" | grep -q "海岳物流股份有限公司" && echo "     驗證成功: 包含 物流" || (echo "     驗證失敗！" && exit 1)

# C. 負責人精確篩選
echo -e "\n  -> 條件 C: 篩選負責人為 '陳柏翰' 的客戶"
RESULT_C=$(curl -s -X GET "$BASE_URL/customers?owner=陳柏翰" -H "Authorization: Bearer $TOKEN")
echo "$RESULT_C" | grep -q "晨曦醫材有限公司" && echo "     驗證成功: 包含 陳柏翰" || (echo "     驗證失敗！" && exit 1)

# 4. 測試資料庫持久化
echo -e "\n[步驟 4] 測試 PostgreSQL 資料持久化..."
RAND_NUM=$((1000 + RANDOM % 9000))
TEST_NAME="PG持久化測試客戶-$RAND_NUM"
echo "建立新測試客戶: $TEST_NAME"

CREATED=$(curl -s -X POST "$BASE_URL/customers" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"$TEST_NAME\",\"email\":\"pgtest@example.com\",\"phone\":\"0999888777\",\"taxId\":\"11223344\",\"industry\":\"雲端服務\",\"ownerName\":\"林宜庭\"}")

echo "再次查詢確認資料是否存在於 PostgreSQL 中..."
SEARCH_RESULT=$(curl -s -X GET "$BASE_URL/customers?keyword=$TEST_NAME" -H "Authorization: Bearer $TOKEN")

if echo "$SEARCH_RESULT" | grep -q "$TEST_NAME"; then
    echo "持久化驗證成功！客戶已確認寫入實體 PostgreSQL 資料庫。"
else
    echo "無法查詢到剛建立的持久化客戶！"
    exit 1
fi

echo -e "\n============================================="
echo "  Unit 3 PostgreSQL 與動態查詢驗證順利通過！"
echo "============================================="
