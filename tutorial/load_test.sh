#!/bin/bash
# ============================================
# OJ 判题性能压测脚本
# 用法：bash load_test.sh
# ============================================

set -e

# 配置
BASE_URL="http://localhost:8101/api"
USER_ACCOUNT="bench"
USER_PASSWORD="12345678"
QUESTION_ID=1
CONCURRENT_LEVELS=(1 10 50 100)
REQUESTS_PER_LEVEL=100

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  OJ 判题性能压测${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

# Step 1: 登录获取 Token
echo -e "${GREEN}[1/4] 登录获取 Token...${NC}"
TOKEN=$(curl -s -X POST "$BASE_URL/user/login" \
  -H "Content-Type: application/json" \
  -d "{\"userAccount\":\"$USER_ACCOUNT\",\"userPassword\":\"$USER_PASSWORD\"}" \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo -e "${RED}登录失败，请检查账号密码${NC}"
  exit 1
fi
echo -e "  Token: ${TOKEN:0:20}..."
echo ""

# Step 2: 准备请求文件
echo -e "${GREEN}[2/4] 准备压测请求文件...${NC}"
REQUEST_FILE=$(mktemp)
cat > "$REQUEST_FILE" << 'EOF'
POST /api/question_submit/do HTTP/1.1
Host: localhost:8101
Content-Type: application/json
Authorization: Bearer TOKEN_PLACEHOLDER
Connection: close

{"questionId":QUESTION_ID_PLACEHOLDER,"language":"python","code":"print(sum(map(int, input().split())))"}
EOF

sed -i "s/TOKEN_PLACEHOLDER/$TOKEN/g" "$REQUEST_FILE"
sed -i "s/QUESTION_ID_PLACEHOLDER/$QUESTION_ID/g" "$REQUEST_FILE"
echo "  请求文件: $REQUEST_FILE"
echo ""

# Step 3: 检查 ab 是否可用
echo -e "${GREEN}[3/4] 检查压测工具...${NC}"
if ! command -v ab &> /dev/null; then
  echo -e "${YELLOW}  ab 未安装，尝试安装...${NC}"
  apt-get update && apt-get install -y apache2-utils 2>/dev/null || \
  yum install -y httpd-tools 2>/dev/null || \
  echo -e "${RED}  安装失败，请手动安装: apt-get install apache2-utils${NC}"
fi

if ! command -v ab &> /dev/null; then
  echo -e "${RED}  ab 不可用，退出${NC}"
  exit 1
fi
echo -e "  ab 已就绪"
echo ""

# Step 4: 压测
echo -e "${GREEN}[4/4] 开始压测...${NC}"
echo ""

for c in "${CONCURRENT_LEVELS[@]}"; do
  n=$((c * 5))  # 请求数 = 并发数 × 5
  echo -e "${YELLOW}--- 并发: $c, 总请求: $n ---${NC}"

  # 使用 ab 压测
  RESULT=$(ab -n "$n" -c "$c" \
    -p "$REQUEST_FILE" \
    -T "application/json" \
    -H "Authorization: Bearer $TOKEN" \
    http://localhost:8101/api/question_submit/do 2>&1)

  # 提取关键指标
  RPS=$(echo "$RESULT" | grep "Requests per second" | awk '{print $4}')
  MEAN_TIME=$(echo "$RESULT" | grep "Time per request" | head -1 | awk '{print $4}')
  FAILED=$(echo "$RESULT" | grep "Failed requests" | awk '{print $3}')

  echo -e "  QPS: ${GREEN}$RPS${NC}"
  echo -e "  平均响应: ${GREEN}${MEAN_TIME}ms${NC}"
  echo -e "  失败数: ${RED}$FAILED${NC}"
  echo ""

  sleep 2  # 间隔 2 秒
done

# 清理
rm -f "$REQUEST_FILE"

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  压测完成！${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""
echo "查看 RabbitMQ 队列状态："
echo "  http://localhost:15672 (guest/guest)"
echo ""
echo "查看后端日志："
echo "  docker compose logs -f backend"
