# 构建阶段
FROM m.daocloud.io/docker.io/library/node:18-alpine AS builder
WORKDIR /app

# 先复制依赖文件，利用缓存
COPY package.json package-lock.json ./

# 安装依赖（使用国内镜像 + 超时设置）
RUN npm ci --registry=https://registry.npmmirror.com --prefer-offline && \
    npm cache clean --force

# 复制源码
COPY . .

# 构建（禁用 source-map 减小体积，加大 node 内存避免 Vite OOM）
ENV NODE_OPTIONS=--max-old-space-size=1536
RUN npm run build

# 运行阶段
FROM m.daocloud.io/docker.io/library/nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
