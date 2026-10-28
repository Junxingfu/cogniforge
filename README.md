# CogniForge · 认知练习室

面向个人用户的认知练习工程，Vue 3 + TypeScript + Spring Boot 3 + MyBatis-Plus。

当前交付是可运行的首个开发版本：四项练习、逐试次记录、服务端评分、复盘、同条件趋势、导出与删除。无教练/学员体系，也未接入商业化支付。

## 快速启动

环境：JDK 17+、Maven 3.9+、Node.js 22+、npm。机器默认 Java 若为 8，请先切换；macOS 示例：

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

终端一：

```sh
cd backend
mvn spring-boot:run
```

终端二：

```sh
cd frontend
npm ci
npm run dev
```

访问 http://127.0.0.1:5173 。后端监听 127.0.0.1:8080，Vite 将 `/api` 代理到后端。默认数据库为 `backend/data/cogniforge.mv.db`，重启不会丢失已保存训练。

## 测试与构建

```sh
cd backend
mvn test
mvn package
```

```sh
cd frontend
npm test
npm run build
```

`frontend/dist` 为静态产物；`backend/target/cogniforge-api-0.1.0.jar` 为后端产物。独立生产部署需同域代理 `/api`，不能直接双击 dist/index.html。

## 切换 MySQL

先创建空数据库 `cogniforge` 和对应应用账号，再配置：

```sh
export DB_URL='jdbc:mysql://localhost:3306/cogniforge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
export DB_USER='cogniforge'
export DB_PASSWORD='自行设置数据库密码'
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

不要把实际密码提交到仓库。MySQL 配置已提供，但本轮运行验证使用 H2；正式上线前需要增加数据库迁移及 MySQL 集成测试。

## 当前功能

- n-back：1–3-back，30个评分试次，9个真实目标；前N项仅建立记忆。分别展示命中率与虚报率。
- Go/No-Go：40试次，30 Go / 10 No-Go；1–3档响应窗口为1000/850/700ms。
- 空间路径：Corsi启发的3×3空间序列任务，3–5个位置，每块5轮；不是标准化Corsi测评。
- 舒尔特：4×4随机布局，顺序寻找；错误点击保留在对应试次的响应序列。
- 规则练习、正式训练、固定复测分开记录；首版固定难度，不伪装成已验证的自适应算法。
- 失焦、主动结束、明显超时标记为中断；刷新后待同步快照可补传为中断记录。
- 配置快照、响应序列、正确率、反应时中位数、质量标记与复盘保存在数据库。
- 同配置且同设备字符串的有效结果进入趋势；可以导出逐试次JSON。

## 个人档案与数据

当前采用匿名本机档案：浏览器保存256位随机Bearer凭证，服务器仅保存凭证的SHA-256归属标识。它不是正式注册登录系统。拥有凭证即可访问该档案；清除浏览器存储会丢失访问能力。跨设备登录、恢复、凭证轮换和过期机制尚未实现。

只在本地运行。上线前需要补齐正式账号/会话管理、HTTPS、限流、容量限制、审计、备份与删除策略、数据库迁移。不要直接把开发服务暴露到公网。

训练提交采用会话ID幂等：相同数据重传不会重复保存，不同数据改写已完成记录返回409。反思备注用独立接口修改。服务端重新评分可以避免信任客户端总分，但不能证明浏览器计时未被篡改。

策略笔记目前只存在localStorage，尚未与账户同步。每个会话目前对应一个训练区块；多任务每日编排、自动升降级、完整记忆宫殿课程属于后续迭代。

## 文档

- [产品设计文档](docs/产品设计文档.md)
- [首版任务规格与体验决策](docs/首版任务规格.md)
- [导航问题修复记录](docs/导航问题修复记录.md)
- [工程设计与API](docs/工程设计.md)
- [开源先例与依赖决策](docs/开源先例.md)

本产品提供练习和自我观察，不提供医疗诊断或智力测评。
