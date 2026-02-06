# Spring Cloud配置优先级说明文档

## 📋 文档概述

本文档详细说明Spring Cloud配置体系中的优先级规则，特别是配置中心(Config Server)与本地配置的优先级关系。

## 🏗️ 配置体系架构

### 整体优先级层次（从高到低）

```
1.  命令行参数 (--server.port=8080)
2.  SPRING_APPLICATION_JSON环境变量
3.  ServletConfig初始化参数
4.  ServletContext初始化参数
5.  JNDI属性
6.  Java系统属性 (System.getProperties())
7.  操作系统环境变量
8.  RandomValuePropertySource (random.*属性)
9.  ⭐ 配置中心配置 (Config Server) - 最重要的外部配置源
10. jar包外的application-{profile}.properties
11. jar包内的application-{profile}.properties
12. jar包外的application.properties
13. jar包内的application.properties
14. @PropertySource注解指定的配置
15. 默认属性 (SpringApplication.setDefaultProperties())
```

## 🔍 Bootstrap上下文机制

### Bootstrap vs Application上下文

**Bootstrap上下文**（优先加载）：
```
bootstrap.properties/bootstrap.yml
    ↓
Config Server连接配置
    ↓
从配置中心获取外部配置
    ↓
注册为最高优先级PropertySource
```

**Application上下文**（后续加载）：
```
application.properties/application.yml
    ↓
本地配置文件
    ↓
优先级低于Bootstrap配置
```

## ⭐ 配置中心优先级详解

### 核心配置项

```properties
# bootstrap.properties - 配置中心客户端配置
spring.application.name=config-client          # 应用名称
spring.cloud.config.uri=http://localhost:8888  # 配置服务器地址
spring.cloud.config.profile=dev                # 环境标识
spring.cloud.config.label=master               # Git分支
```

### 配置获取流程

1. **Bootstrap阶段**：解析`spring.cloud.config.*`配置
2. **构造请求URL**：`{uri}/{application}/{profile}/{label}`
3. **发送HTTP请求**：向Config Server发起请求
4. **接收配置响应**：解析返回的配置数据
5. **注册配置源**：将配置注册为CompositePropertySource
6. **设置优先级**：配置中心配置具有最高优先级

### Git仓库配置（当前项目设置）

```properties
# config-server/application.properties
spring.cloud.config.server.git.uri=https://github.com/forezp/SpringcloudConfig/
spring.cloud.config.server.git.searchPaths=respo
spring.cloud.config.label=master
```

## 📊 配置优先级实例分析

### 场景1：纯配置中心模式

```properties
# bootstrap.properties
spring.application.name=config-client
spring.cloud.config.uri=http://localhost:8888

# Git配置文件 config-client-dev.yml
foo: git-value
database.url: git-db-url
```
**结果**：所有配置都来自Git配置中心

### 场景2：混合配置模式

```properties
# bootstrap.properties
spring.application.name=config-client
spring.cloud.config.uri=http://localhost:8888
local.setting: bootstrap-value

# application.properties  
foo: local-value
database.url: local-db-url

# Git配置中心 config-client-dev.yml
foo: git-value
```
**结果**：
- `foo` = "git-value" （配置中心优先）
- `database.url` = "git-db-url" （配置中心优先）  
- `local.setting` = "bootstrap-value" （仅存在于bootstrap）

### 场景3：配置中心不可用时的降级

```properties
# bootstrap.properties
spring.application.name=config-client
spring.cloud.config.uri=http://localhost:8888
foo: fallback-bootstrap-value

# application.properties
foo: fallback-application-value
```
**结果**：`foo` = "fallback-bootstrap-value" （Bootstrap作为降级方案）

## ⚙️ 高级配置选项

### 控制配置覆盖行为

```properties
# 允许本地配置覆盖远程配置
spring.cloud.config.override-none=true

# 允许特定属性被覆盖
spring.cloud.config.override-system-properties=false

# 忽略配置中心失败（不抛出异常）
spring.cloud.config.fail-fast=false

# 配置重试机制
spring.cloud.config.retry.max-attempts=6
spring.cloud.config.retry.initial-interval=1000
```

### Profile管理

```
config-client-dev.yml    # 开发环境配置
config-client-test.yml   # 测试环境配置  
config-client-prod.yml   # 生产环境配置
```

### 多Profile支持

```properties
# 激活多个profile
spring.profiles.active=dev,mysql,redis
```
会依次加载：config-client-dev.yml, config-client-mysql.yml, config-client-redis.yml

## 🛠️ 调试和监控工具

### 查看配置源信息

```java
@Autowired
private ConfigurableEnvironment environment;

@RequestMapping("/config-sources")
public String getConfigSources() {
    StringBuilder sb = new StringBuilder();
    for (PropertySource<?> ps : environment.getPropertySources()) {
        sb.append(ps.getName()).append("\n");
    }
    return sb.toString();
}
```

### 动态刷新配置

```java
@RefreshScope
@Component
public class MyConfiguration {
    @Value("${my.config.value}")
    private String configValue;
}
```

## 📋 最佳实践建议

### 1. 配置分离原则
- **基础配置**：放Git（数据库连接、Redis配置等）
- **敏感配置**：使用加密存储
- **本地调试**：放`application-local.properties`

### 2. 环境管理策略
- 不同环境使用不同profile
- 配置中心统一管理核心配置
- 本地开发使用本地配置作为fallback

### 3. 安全考虑
- 敏感信息加密存储
- 访问控制和认证机制
- 配置变更审计日志

### 4. 性能优化
- 合理设置缓存时间
- 避免频繁的配置刷新
- 监控配置中心可用性

## ⚠️ 常见问题解答

### Q1: 为什么配置中心的配置优先级最高？
**A**: 这是Spring Cloud的设计原则，目的是实现：
- 配置的集中化管理
- 不同环境的配置隔离
- 支持配置的动态更新

### Q2: 如何让本地配置优先于配置中心？
**A**: 设置以下配置：
```properties
spring.cloud.config.override-none=true
```

### Q3: 配置中心连接失败怎么办？
**A**: 可以设置：
```properties
spring.cloud.config.fail-fast=false
```
系统会使用本地配置作为降级方案。

### Q4: 如何查看当前生效的配置来源？
**A**: 通过`/config-sources`端点或启用debug日志查看。

---
**文档版本**：v1.0  
**最后更新**：2026-02-06  
**适用版本**：Spring Boot 1.5.2.RELEASE, Spring Cloud Camden.SR6/Dalston.RC1