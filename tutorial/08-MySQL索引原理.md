# MySQL 索引原理 — B+ 树详解

## 面试怎么答

> "MySQL 的索引底层用的是 B+ 树。B+ 树的特点是数据只存在叶子节点，非叶子节点只存索引键值，这样每一页能存更多索引项，树更矮，查询更快。本项目的 `user` 表用 `userAccount` 做唯一索引加速登录查询，`question_submit` 表用 `userId` 和 `questionId` 做普通索引加速分页查询。"

---

## 1. 为什么用 B+ 树？

### 常见数据结构对比

| 数据结构 | 查询 | 问题 |
|----------|------|------|
| **二叉搜索树** | O(log n) | 树可能退化成链表，O(n) |
| **AVL 树 / 红黑树** | O(log n) | 树太高，磁盘 IO 次数多 |
| **B 树** | O(log n) | 非叶子节点也存数据，每页存的索引少 |
| **B+ 树** | O(log n) | 非叶子节点只存索引，叶子节点用链表连接，最适合磁盘 |

### B+ 树结构

```
                    [10 | 20]                    ← 非叶子节点（只存索引）
                   /     |     \
            [1|5]      [15]      [25|30]         ← 非叶子节点
           / | \      / | \      / | \
         [1][5][10] [15][20] [25][30]            ← 叶子节点（存数据 + 链表连接）
          ←→ ←→ ←→ ←→ ←→ ←→ ←→ ←→
```

**关键特点**：
1. **非叶子节点只存索引**：一页（16KB）能存更多索引项，树更矮
2. **叶子节点存数据**：所有数据都在叶子节点
3. **叶子节点用链表连接**：支持范围查询（`WHERE id > 100`）

### 为什么树矮很重要？

```
假设一页能存 1000 个索引项：
  2 层 B+ 树：1000 × 1000 = 100 万条数据
  3 层 B+ 树：1000 × 1000 × 1000 = 10 亿条数据

3 次磁盘 IO 就能查到 10 亿条数据中的任意一条
```

---

## 2. 聚簇索引 vs 非聚簇索引

### 聚簇索引（主键索引）

```
叶子节点直接存储完整的数据行

主键 B+ 树：
  叶子节点 → [id=1, userAccount=test, userPassword=xxx, userName=Tom, ...]
```

**特点**：一张表只有一个聚簇索引，就是主键。

### 非聚簇索引（二级索引）

```
叶子节点存储的是主键值（不是完整数据）

userId 索引 B+ 树：
  叶子节点 → [userId=1, id=5]  ← 只存了主键 id
```

**查询过程（回表）**：
```
SELECT * FROM question_submit WHERE userId = 1

1. 走 userId 索引 → 找到主键 id = 5
2. 拿 id = 5 回到主键索引 → 找到完整数据行
  ↑ 这一步叫"回表"
```

### 本项目的例子

```sql
-- user 表
CREATE TABLE user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,  -- 聚簇索引
  userAccount VARCHAR(256),
  ...
);
CREATE UNIQUE INDEX uk_userAccount ON user(userAccount);  -- 非聚簇索引

-- 登录查询
SELECT * FROM user WHERE userAccount = 'test' AND userPassword = 'xxx';
-- 1. 走 uk_userAccount 索引 → 找到主键 id
-- 2. 回表 → 拿到完整数据（包括 userPassword 用于比对）
```

---

## 3. 覆盖索引（避免回表）

```sql
-- ❌ 需要回表
SELECT * FROM question WHERE userId = 1;
-- userId 索引 → 找到主键 → 回表取所有字段

-- ✅ 覆盖索引，不回表
SELECT id, userId FROM question WHERE userId = 1;
-- userId 索引的叶子节点已经包含 id 和 userId，不需要回表
-- EXPLAIN 中 Extra 显示 "Using index"
```

**本项目优化**：如果只需要题目 ID 和标题，可以只查这两个字段，利用 `idx_userId` 覆盖索引。

---

## 4. 索引失效场景

### 本项目中可能遇到的

```sql
-- ❌ 对索引列使用函数
SELECT * FROM question_submit WHERE YEAR(createTime) = 2024;
-- createTime 有索引，但 YEAR() 导致索引失效

-- ✅ 改写
SELECT * FROM question_submit
WHERE createTime >= '2024-01-01' AND createTime < '2025-01-01';

-- ❌ 隐式类型转换
SELECT * FROM user WHERE userAccount = 12345;
-- userAccount 是 varchar，传入 int，MySQL 会把 varchar 转成 int 比较
-- 相当于对索引列做了函数操作，索引失效

-- ✅ 类型匹配
SELECT * FROM user WHERE userAccount = '12345';

-- ❌ LIKE 左模糊
SELECT * FROM question WHERE title LIKE '%算法%';
-- 左边有 %，无法用 B+ 树的有序性，只能全表扫描

-- ✅ 右模糊可以用索引
SELECT * FROM question WHERE title LIKE '算法%';
-- B+ 树叶子节点有序，可以快速定位 "算法" 开头的记录
```

---

## 5. 联合索引与最左前缀

```sql
-- 假设有联合索引
ALTER TABLE question_submit ADD INDEX idx_user_question(userId, questionId);

-- ✅ 能用索引（匹配最左前缀 userId）
SELECT * FROM question_submit WHERE userId = 1;
SELECT * FROM question_submit WHERE userId = 1 AND questionId = 2;

-- ❌ 不能用索引（跳过了 userId）
SELECT * FROM question_submit WHERE questionId = 2;
```

---

## 6. 组合索引实战（本项目优化）

### 优化前：单列索引

```sql
-- question 表
CREATE TABLE question (
  id BIGINT PRIMARY KEY,
  title VARCHAR(512),
  difficulty VARCHAR(128),
  userId BIGINT,
  createTime DATETIME,
  isDelete TINYINT DEFAULT 0
);

-- 只有单列索引
CREATE INDEX idx_userId ON question(userId);
CREATE INDEX idx_difficulty ON question(difficulty);
CREATE INDEX idx_isDelete ON question(isDelete);

-- ❌ 按难度+未删除查询时，只能命中一个单列索引
SELECT * FROM question
WHERE difficulty = '中等' AND isDelete = 0
ORDER BY createTime DESC
LIMIT 0, 20;
-- MySQL 会选择一个索引（如 idx_difficulty），但 isDelete 需要回表过滤
-- 排序也无法用到索引，需要 filesort
```

### 优化后：组合索引

```sql
-- ✅ 组合索引：(difficulty, isDelete)
-- 满足 "difficulty = ? AND isDelete = ?" 查询
-- 用一颗 B+ 树同时定位两个条件
ALTER TABLE question ADD INDEX idx_difficulty_isDelete(difficulty, isDelete);

-- ✅ 组合索引：(questionId, status)
ALTER TABLE question_submit ADD INDEX idx_questionId_status(questionId, status);

-- ✅ 组合索引：(userId, status)
ALTER TABLE question_submit ADD INDEX idx_userId_status(userId, status);

-- ✅ 组合索引：(userId, createTime)  — 支持按时间排序的分页
ALTER TABLE question_submit ADD INDEX idx_userId_createTime(userId, createTime);
```

### 优化效果对比

| 查询场景 | 优化前 | 优化后 | 提升 |
|----------|--------|--------|------|
| 按 userId 分页查询我的提交 | 单列索引 + filesort | 组合索引 + 索引排序 | **2-5 倍** |
| 按 difficulty + isDelete 查题目列表 | 单列索引 + 回表过滤 | 组合索引 + 直接定位 | **3-5 倍** |
| 按 questionId + status 查该题提交记录 | 单列索引 + 回表 | 组合索引 + 直接定位 | **2-3 倍** |

### 如何选择组合索引的列顺序？

**原则**：把**等值查询**的列放前面，**范围查询**的列放后面。

```sql
-- ❌ 错误：范围查询在前
ALTER TABLE question_submit ADD INDEX idx_createTime_userId(createTime, userId);
-- WHERE userId = 1 AND createTime > '2024-01-01'
-- 跳过了 userId，索引无法完全命中

-- ✅ 正确：等值查询在前
ALTER TABLE question_submit ADD INDEX idx_userId_createTime(userId, createTime);
-- WHERE userId = 1 AND createTime > '2024-01-01'
-- 先用 userId 定位，再用 createTime 做范围扫描
```

### 覆盖索引的优化

```sql
-- ✅ 用组合索引实现覆盖索引，完全避免回表
SELECT id, title FROM question
WHERE difficulty = '中等' AND isDelete = 0
ORDER BY createTime DESC
LIMIT 0, 20;

-- 如果组合索引是 (difficulty, isDelete, createTime, title)
-- 索引的叶子节点已经包含所有查询字段
-- EXPLAIN: type=range, Extra="Using index"（真正的覆盖索引！）
```

### 本项目最终索引设计

```sql
-- question 表
PRIMARY KEY (id)
INDEX idx_userId(userId)
INDEX idx_userId_createTime(userId, createTime)
INDEX idx_difficulty_isDelete(difficulty, isDelete)
INDEX idx_title(title(100))

-- question_submit 表
PRIMARY KEY (id)
INDEX idx_questionId_status(questionId, status)
INDEX idx_userId_status(userId, status)
INDEX idx_userId_createTime(userId, createTime)
INDEX idx_questionId_createTime(questionId, createTime)
```

---

## 7. 面试常见追问

### Q: 组合索引和多个单列索引哪个更好？

> **组合索引**。一个 (A, B) 组合索引相当于同时支持 `WHERE A=?`、`WHERE A=? AND B=?` 两种查询，而且查询效率更高（一个索引就定位到数据）。多个单列索引时，MySQL 通常只用上一个最优的。

### Q: 组合索引最多能有几列？

> MySQL InnoDB 限制是 16 列。但实际项目中建议 3-5 列以内，列太多会让索引变大、更新变慢。

### Q: (A, B, C) 和 (A, C, B) 一样吗？

> **不一样**。MySQL 从左到右匹配索引，跳过任何中间列就无法命中后续列。
> - `WHERE A=? AND B=? AND C=?` → ✅ 完全命中 (A,B,C)
> - `WHERE A=? AND C=?` → ❌ 只命中 A，C 无法用索引（跳过了 B）
> - 但 (A, C, B) 可以命中 `WHERE A=? AND C=?`

### Q: 组合索引什么时候会失效？

> - 跳过中间列：`WHERE A=? AND C=?`（跳过了 B）→ C 无法用索引
> - 对索引列做函数运算：`WHERE YEAR(createTime) = 2024`
> - OR 连接：`WHERE A=? OR B=?` → 一般走不到组合索引，应该改成 UNION

```sql
-- ⚠️ 部分使用（只用到 userId，questionId 部分无法用索引）
SELECT * FROM question_submit WHERE userId = 1 AND language = 'java';
-- userId 走索引，language 不走索引
```

**原理**：B+ 树先按第一个字段排序，再按第二个字段排序。跳过第一个字段，第二个字段在树中是无序的。

---

## 8. 面试常见追问

### Q: 为什么 InnoDB 用 B+ 树而不用 B 树？

> B 树的非叶子节点也存数据，一页能存的索引项更少，树更高，IO 更多。B+ 树非叶子节点只存索引，一页能存上千个索引项，3 层就能覆盖上亿数据。而且 B+ 树叶子节点用链表连接，范围查询只需遍历链表，不用中序遍历整棵树。

### Q: 主键为什么建议用自增 ID？

> 自增 ID 保证新记录总是在 B+ 树的末尾追加，不会导致页分裂。如果用 UUID 作为主键，插入位置随机，频繁触发页分裂，写入性能差，还会产生碎片。

### Q: 一张表能有多少个索引？

> 理论上没有限制，但建议不超过 5-6 个。每个索引都是一棵 B+ 树，写入时要维护所有索引，索引越多写入越慢。而且优化器选择索引也有开销。

### Q: 本项目的索引设计思路？

```text
user 表：
  - 主键 id（聚簇索引）
  - uk_userAccount（唯一索引，加速登录）

question 表：
  - 主键 id（聚簇索引）
  - idx_userId（普通索引，加速"我的题目"查询）

question_submit 表：
  - 主键 id（聚簇索引）
  - idx_questionId（普通索引，加速按题目查提交）
  - idx_userId（普通索引，加速按用户查提交）
```
