# Feign实战应用指南

## 实际应用场景

### 1. 微服务间调用模式

#### 1.1 同步调用场景
```java
// 用户服务调用订单服务查询订单信息
@FeignClient("order-service")
public interface OrderServiceClient {
    
    @GetMapping("/orders/{orderId}")
    OrderDTO getOrderById(@PathVariable("orderId") Long orderId);
    
    @PostMapping("/orders")
    OrderDTO createOrder(@RequestBody CreateOrderRequest request);
}
```

#### 1.2 数据聚合场景
```java
@Service
public class UserService {
    
    @Autowired
    private OrderServiceClient orderClient;
    
    @Autowired
    private ProductServiceClient productClient;
    
    public UserOrderInfo getUserOrderInfo(Long userId) {
        // 并行调用多个服务获取用户相关信息
        UserDTO user = userClient.getUserById(userId);
        List<OrderDTO> orders = orderClient.getOrdersByUserId(userId);
        List<ProductDTO> products = productClient.getProductsByCategory(user.getCategory());
        
        return UserOrderInfo.builder()
                .user(user)
                .orders(orders)
                .recommendedProducts(products)
                .build();
    }
}
```

### 2. 高级配置示例

#### 2.1 复杂参数处理
```java
@FeignClient("data-service")
public interface DataServiceClient {
    
    // GET请求带复杂查询参数
    @GetMapping("/data/query")
    List<DataRecord> queryData(
        @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam("types") List<String> types,
        @RequestParam("page") Integer page,
        @RequestParam("size") Integer size
    );
    
    // POST请求发送复杂对象
    @PostMapping("/data/batch")
    BatchResult processDataBatch(@RequestBody DataBatchRequest batchRequest);
    
    // 文件上传
    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    UploadResult uploadFile(@RequestPart("file") MultipartFile file);
}
```

#### 2.2 自定义配置类
```java
@Configuration
public class FeignClientConfig {
    
    // 自定义编码器
    @Bean
    public Encoder feignEncoder() {
        return new SpringFormEncoder(new SpringEncoder(new ObjectFactory<HttpMessageConverters>() {
            @Override
            public HttpMessageConverters getObject() {
                return new HttpMessageConverters(new MappingJackson2HttpMessageConverter());
            }
        }));
    }
    
    // 自定义解码器
    @Bean
    public Decoder feignDecoder() {
        return new ResponseEntityDecoder(new SpringDecoder(new ObjectFactory<HttpMessageConverters>() {
            @Override
            public HttpMessageConverters getObject() {
                return new HttpMessageConverters(new MappingJackson2HttpMessageConverter());
            }
        }));
    }
    
    // 请求拦截器 - 添加认证信息
    @Bean
    public RequestInterceptor authRequestInterceptor() {
        return template -> {
            String token = SecurityContextHolder.getContext().getAuthentication().getCredentials().toString();
            template.header("Authorization", "Bearer " + token);
        };
    }
}
```

### 3. 异常处理最佳实践

#### 3.1 统一异常处理
```java
@Component
public class FeignErrorDecoder implements ErrorDecoder {
    
    private final ErrorDecoder defaultErrorDecoder = new Default();
    
    @Override
    public Exception decode(String methodKey, Response response) {
        try {
            // 解析错误响应体
            String responseBody = Util.toString(response.body().asReader());
            ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
            
            switch (response.status()) {
                case 400:
                    return new BadRequestException(errorResponse.getMessage());
                case 401:
                    return new UnauthorizedException(errorResponse.getMessage());
                case 404:
                    return new ResourceNotFoundException(errorResponse.getMessage());
                case 500:
                    return new InternalServerException(errorResponse.getMessage());
                default:
                    return defaultErrorDecoder.decode(methodKey, response);
            }
        } catch (IOException e) {
            return new FeignException(response.status(), "Failed to parse error response");
        }
    }
}
```

#### 3.2 熔断降级处理
```java
// 方式一：Fallback类
@Component
public class UserServiceFallback implements UserServiceClient {
    
    @Override
    public UserDTO getUserById(Long userId) {
        log.warn("UserService fallback triggered for userId: {}", userId);
        return UserDTO.builder()
                .id(userId)
                .name("Fallback User")
                .status(UserStatus.UNAVAILABLE)
                .build();
    }
    
    @Override
    public List<UserDTO> getUsersByDepartment(String department) {
        log.warn("UserService fallback triggered for department: {}", department);
        return Collections.emptyList();
    }
}

@FeignClient(name = "user-service", fallback = UserServiceFallback.class)
public interface UserServiceClient {
    @GetMapping("/users/{userId}")
    UserDTO getUserById(@PathVariable("userId") Long userId);
    
    @GetMapping("/users/department/{department}")
    List<UserDTO> getUsersByDepartment(@PathVariable("department") String department);
}

// 方式二：FallbackFactory（可获取异常信息）
@Component
public class UserServiceFallbackFactory implements FallbackFactory<UserServiceClient> {
    
    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {
            @Override
            public UserDTO getUserById(Long userId) {
                log.error("UserService failed, userId: {}, cause: {}", userId, cause.getMessage());
                return UserDTO.builder()
                        .id(userId)
                        .name("Fallback User - " + cause.getMessage())
                        .build();
            }
            
            @Override
            public List<UserDTO> getUsersByDepartment(String department) {
                log.error("UserService failed, department: {}, cause: {}", department, cause.getMessage());
                return Collections.emptyList();
            }
        };
    }
}

@FeignClient(name = "user-service", fallbackFactory = UserServiceFallbackFactory.class)
public interface UserServiceClient {
    // 接口定义...
}
```

### 4. 性能优化配置

#### 4.1 连接池优化
```yaml
# application.yml
feign:
  httpclient:
    enabled: true
    max-connections: 1000          # 最大连接数
    max-connections-per-route: 200 # 每路由最大连接数
  okhttp:
    enabled: false                 # 可选择使用OkHttp替代HttpClient
  compression:
    request:
      enabled: true
      mime-types: application/json,text/xml,application/xml,application/json
      min-request-size: 2048
    response:
      enabled: true

# 超时配置
ribbon:
  ConnectTimeout: 3000    # 连接超时
  ReadTimeout: 10000      # 读取超时
  MaxAutoRetries: 1       # 同一实例重试次数
  MaxAutoRetriesNextServer: 2  # 切换实例重试次数
```

#### 4.2 缓存优化
```java
@Service
public class CachedDataService {
    
    @Autowired
    private ExternalServiceClient externalClient;
    
    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
    
    public DataDTO getDataWithCache(String key) {
        return (DataDTO) cache.get(key, k -> externalClient.fetchData(k));
    }
}
```

### 5. 测试策略

#### 5.1 单元测试
```java
@RunWith(MockitoJUnitRunner.class)
public class UserServiceTest {
    
    @Mock
    private UserServiceClient userServiceClient;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    public void testGetUserProfile() {
        // 准备测试数据
        UserDTO mockUser = UserDTO.builder().id(1L).name("John").build();
        when(userServiceClient.getUserById(1L)).thenReturn(mockUser);
        
        // 执行测试
        UserProfile profile = userService.getUserProfile(1L);
        
        // 验证结果
        assertEquals("John", profile.getName());
        verify(userServiceClient).getUserById(1L);
    }
}
```

#### 5.2 集成测试
```java
@SpringBootTest
@AutoConfigureWireMock(port = 0)
public class UserServiceIntegrationTest {
    
    @Autowired
    private UserServiceClient userServiceClient;
    
    @BeforeEach
    void setUp() {
        stubFor(get(urlEqualTo("/users/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"John\"}")));
    }
    
    @Test
    public void testGetUserById() {
        UserDTO user = userServiceClient.getUserById(1L);
        assertEquals("John", user.getName());
    }
}
```

### 6. 监控与运维

#### 6.1 指标收集
```java
@Component
public class FeignMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Timer.Sample sample;
    
    public FeignMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    @EventListener
    public void handleFeignRequest(FeignRequestEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        // 记录请求开始
    }
    
    @EventListener
    public void handleFeignResponse(FeignResponseEvent event) {
        // 记录请求结束，统计耗时
        sample.stop(Timer.builder("feign.requests")
                .tag("service", event.getServiceName())
                .tag("method", event.getMethod())
                .tag("status", String.valueOf(event.getStatus()))
                .register(meterRegistry));
    }
}
```

#### 6.2 健康检查
```java
@Component
public class FeignHealthIndicator implements HealthIndicator {
    
    @Autowired
    private DownstreamServiceClient downstreamClient;
    
    @Override
    public Health health() {
        try {
            String status = downstreamClient.healthCheck();
            if ("UP".equals(status)) {
                return Health.up().build();
            } else {
                return Health.down().withDetail("status", status).build();
            }
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

### 7. 生产环境注意事项

#### 7.1 安全配置
```java
@Configuration
public class FeignSecurityConfig {
    
    // SSL配置
    @Bean
    public Client feignClient() throws Exception {
        SSLContext sslContext = SSLContextBuilder
                .create()
                .loadTrustMaterial(null, (chain, authType) -> true)
                .build();
        
        SSLConnectionSocketFactory socketFactory = 
                new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        
        CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(socketFactory)
                .build();
                
        return new ApacheHttpClient(httpClient);
    }
}
```

#### 7.2 日志配置
```yaml
# 生产环境日志配置
logging:
  level:
    com.yourcompany.feign: WARN  # 只记录警告及以上级别
    feign.Logger: WARN
    
# 或者完全禁用Feign日志
feign:
  client:
    config:
      default:
        loggerLevel: NONE
```

## 总结

Feign在微服务架构中扮演着重要的角色，正确的使用和配置能够显著提升系统的可靠性和性能。在实际应用中，需要根据具体的业务场景选择合适的配置和优化策略。