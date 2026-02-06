# Spring Cloud Zuul 网关知识点整理

## 1. 项目概述

本项目是一个基于Spring Cloud的微服务架构示例，其中`service-zuul`模块作为API网关，负责请求路由、负载均衡和服务治理。

### 1.1 项目架构图

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Eureka Server │◄──►│   Zuul Gateway  │◄──►│ Service Ribbon  │
│   (port: 8761)  │    │   (port: 8769)  │    │   (port: 8764)  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              ▲                       ▲
                              │                       │
                              ▼                       ▼
                       ┌─────────────────┐    ┌─────────────────┐
                       │ Service Feign   │    │   Service Hi    │
                       │   (port: 8765)  │    │   (port: 8762)  │
                       └─────────────────┘    └─────────────────┘
```

## 2. Zuul核心概念

### 2.1 什么是Zuul？
Zuul是Netflix开源的微服务网关组件，主要功能包括：
- **路由转发**：将外部请求路由到内部微服务
- **负载均衡**：集成Ribbon实现客户端负载均衡
- **过滤器机制**：提供请求预处理和响应后处理能力
- **服务治理**：与Eureka集成实现服务发现

### 2.2 Zuul的工作原理

```
客户端请求 → Zuul网关 → 过滤器链 → 目标服务
                    ↑
              Pre → Route → Post → Error
```

## 3. 项目配置分析

### 3.1 Maven依赖配置

```xml
<!-- 核心依赖 -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-zuul</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-eureka</artifactId>
</dependency>
```

### 3.2 启动类注解

```java
@EnableZuulProxy    // 启用Zuul代理功能
@EnableEurekaClient  // 注册到Eureka服务
@SpringBootApplication
public class ServiceZuulApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceZuulApplication.class, args);
    }
}
```

### 3.3 路由配置

```yaml
zuul:
  routes:
    api-a:
      path: /api-a/**
      serviceId: service-ribbon
    api-b:
      path: /api-b/**
      serviceId: service-feign
```

**路由规则说明：**
- `/api-a/**` 路径的请求转发到 `service-ribbon` 服务
- `/api-b/**` 路径的请求转发到 `service-feign` 服务

## 4. 过滤器详解

### 4.1 过滤器类型

Zuul提供四种类型的过滤器：

| 类型 | 执行时机 | 主要用途 |
|------|----------|----------|
| **pre** | 路由前 | 身份验证、日志记录、参数校验 |
| **route** | 路由时 | 构造发送给目标服务的请求 |
| **post** | 路由后 | 修改响应、添加响应头 |
| **error** | 异常时 | 统一异常处理 |

### 4.2 自定义过滤器实现

```java
@Component
public class MyFilter extends ZuulFilter {
    
    @Override
    public String filterType() {
        return "pre";  // 前置过滤器
    }
    
    @Override
    public int filterOrder() {
        return 0;  // 执行顺序，数字越小优先级越高
    }
    
    @Override
    public boolean shouldFilter() {
        return true;  // 是否执行该过滤器
    }
    
    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();
        
        // 日志记录
        log.info(String.format("%s >>> %s", 
               request.getMethod(), request.getRequestURL().toString()));
        
        // Token验证
        Object accessToken = request.getParameter("token");
        if(accessToken == null) {
            log.warn("token is empty");
            ctx.setSendZuulResponse(false);  // 不再继续路由
            ctx.setResponseStatusCode(401);   // 设置响应状态码
            try {
                ctx.getResponse().getWriter().write("token is empty");
            } catch (Exception e) {}
            return null;
        }
        log.info("ok");
        return null;
    }
}
```

### 4.3 过滤器执行流程

```
请求到达 → Pre过滤器 → Route过滤器 → 目标服务
    ↓         ↓           ↓            ↓
  记录日志   Token验证   负载均衡    业务处理
    ↓         ↓           ↓            ↓
  成功放行   验证通过     转发请求    返回结果
    ↓         ↓           ↓            ↓
           ← Post过滤器 ← 响应返回 ←
```

## 5. 核心API使用

### 5.1 RequestContext上下文

```java
RequestContext ctx = RequestContext.getCurrentContext();
HttpServletRequest request = ctx.getRequest();
HttpServletResponse response = ctx.getResponse();

// 控制路由行为
ctx.setSendZuulResponse(false);  // 停止路由
ctx.setResponseStatusCode(401);   // 设置状态码

// 获取/设置请求属性
Object token = ctx.getRequest().getParameter("token");
ctx.set("custom-key", "custom-value");
```

### 5.2 路由配置方式

#### 方式一：YAML配置（推荐）
```yaml
zuul:
  routes:
    api-a:
      path: /api-a/**
      serviceId: service-ribbon
    api-b:
      path: /api-b/**
      serviceId: service-feign
```

#### 方式二：Java配置
```java
@Bean
public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("api-a", r -> r.path("/api-a/**")
            .uri("lb://service-ribbon"))
        .route("api-b", r -> r.path("/api-b/**")
            .uri("lb://service-feign"))
        .build();
}
```

## 6. 实际应用示例

### 6.1 访问路径映射

假设访问以下URL：
- `http://localhost:8769/api-a/hi?name=forezp&token=22`
- `http://localhost:8769/api-b/hi?name=forezp&token=22`

**路由过程：**
1. 请求到达Zuul网关(port: 8769)
2. MyFilter前置过滤器执行Token验证
3. 根据路由配置转发到对应服务：
   - `/api-a/**` → service-ribbon服务
   - `/api-b/**` → service-feign服务

### 6.2 完整请求处理流程

```
客户端请求
    ↓
http://localhost:8769/api-a/hi?name=forezp&token=22
    ↓
Zuul网关接收
    ↓
MyFilter.pre过滤器(Token验证)
    ↓
路由到service-ribbon服务
    ↓
service-ribbon调用service-hi
    ↓
返回响应给客户端
```

## 7. 最佳实践

### 7.1 过滤器设计原则

1. **单一职责**：每个过滤器只做一件事
2. **合理排序**：重要的安全验证放在前面
3. **异常处理**：在error过滤器中统一处理异常
4. **性能考虑**：避免在过滤器中做耗时操作

### 7.2 常见过滤器组合

```java
// 安全认证过滤器
public class AuthFilter extends ZuulFilter { /* pre */ }

// 日志记录过滤器  
public class LogFilter extends ZuulFilter { /* pre */ }

// 响应包装过滤器
public class ResponseFilter extends ZuulFilter { /* post */ }

// 全局异常处理过滤器
public class ErrorFilter extends ZuulFilter { /* error */ }
```

### 7.3 配置优化建议

```yaml
zuul:
  # 忽略某些敏感头信息
  ignored-headers: Access-Control-Allow-Credentials,Access-Control-Allow-Origin
  # 设置超时时间
  host:
    connect-timeout-millis: 5000
    socket-timeout-millis: 10000
  # 开启重试机制
  retryable: true
```

## 8. 故障排除

### 8.1 常见问题

1. **路由不生效**
   - 检查`@EnableZuulProxy`注解
   - 确认路由配置格式正确
   - 验证目标服务是否正常运行

2. **过滤器不执行**
   - 确保过滤器类上有`@Component`注解
   - 检查`shouldFilter()`返回值
   - 查看过滤器执行顺序

3. **Token验证失败**
   - 确认请求参数名称正确
   - 检查过滤器逻辑
   - 查看日志输出

### 8.2 调试技巧

```java
// 启用Zuul详细日志
logging:
  level:
    com.netflix.zuul: DEBUG
    org.springframework.cloud.netflix.zuul: DEBUG
```

## 9. 扩展功能

### 9.1 动态路由
可以通过数据库或配置中心动态管理路由规则

### 9.2 限流控制
集成Hystrix或Sentinel实现接口限流

### 9.3 灰度发布
基于Header或参数实现流量切分

### 9.4 监控告警
集成Prometheus、Grafana等监控工具

## 10. 版本兼容性

本项目使用：
- Spring Boot: 1.5.2.RELEASE
- Spring Cloud: Dalston.RC1
- Zuul: 1.x版本

**注意**：Zuul 2.x已重构为异步非阻塞模型，与1.x不兼容。

---
*文档更新时间：2026-02-06*