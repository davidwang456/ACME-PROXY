# Proxy Service for cert-manager to step-ca

## 项目概述

Proxy Service是一个基于Spring Boot开发的代理服务，专门用于解决Kubernetes环境中cert-manager无法直接访问部署在外网的step-ca服务的问题。该服务作为中间代理层，转发cert-manager的ACME请求到step-ca，同时提供了额外的证书管理功能，包括手动生成证书、注销证书和下载证书。

### 主要功能

1. **请求代理转发**：将cert-manager发送的ACME协议请求透明地转发到step-ca服务
2. **SSL/TLS支持**：支持HTTPS连接，可配置信任所有证书或使用自定义证书验证
3. **证书管理**：提供RESTful API用于手动管理证书生命周期
4. **健康检查**：提供服务健康状态监控端点
5. **日志记录**：详细的请求和响应日志，便于问题排查

### 技术架构

- **框架**：Spring Boot 3.2.0
- **Java版本**：Java 17
- **HTTP客户端**：Apache HttpClient 5
- **证书处理**：Bouncy Castle
- **构建工具**：Maven
- **配置格式**：YAML

## 快速开始

### 环境要求

- Java 17或更高版本
- Maven 3.6或更高版本
- 可访问的step-ca服务实例

### 编译和运行

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd proxy-service
   ```

2. **编译项目**
   ```bash
   mvn clean compile
   ```

3. **运行测试**
   ```bash
   mvn test
   ```

4. **打包应用**
   ```bash
   mvn clean package
   ```

5. **运行应用**
   ```bash
   java -jar target/proxy-service-1.0.0.jar
   ```

   或者使用Maven插件运行：
   ```bash
   mvn spring-boot:run
   ```

### Docker部署

1. **创建Dockerfile**
   ```dockerfile
   FROM openjdk:17-jre-slim
   
   WORKDIR /app
   COPY target/proxy-service-1.0.0.jar app.jar
   
   EXPOSE 8080
   
   ENTRYPOINT ["java", "-jar", "app.jar"]
   ```

2. **构建Docker镜像**
   ```bash
   docker build -t proxy-service:1.0.0 .
   ```

3. **运行Docker容器**
   ```bash
   docker run -d \
     --name proxy-service \
     -p 8080:8080 \
     -e STEP_CA_URL=https://step.it.com \
     proxy-service:1.0.0
   ```

## 配置说明

### 应用配置

主要配置项位于`src/main/resources/application.yml`文件中：

```yaml
# 服务器配置
server:
  port: 8080                    # 服务端口

# Step-CA配置
step-ca:
  url: https://step.it.com      # Step-CA服务地址
  timeout: 30000                # 请求超时时间（毫秒）

# 代理配置
proxy:
  trust-all-certs: true        # 是否信任所有SSL证书
  max-connections: 100          # 最大连接数
  max-connections-per-route: 20 # 每个路由的最大连接数
```

### 环境变量配置

可以通过环境变量覆盖配置文件中的设置：

- `STEP_CA_URL`：Step-CA服务地址
- `PROXY_TRUST_ALL_CERTS`：是否信任所有证书
- `SERVER_PORT`：服务端口

### Kubernetes配置

在Kubernetes环境中部署时，可以使用ConfigMap和Secret来管理配置：

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: proxy-service-config
data:
  application.yml: |
    step-ca:
      url: https://step.it.com
    proxy:
      trust-all-certs: true
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: proxy-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: proxy-service
  template:
    metadata:
      labels:
        app: proxy-service
    spec:
      containers:
      - name: proxy-service
        image: proxy-service:1.0.0
        ports:
        - containerPort: 8080
        volumeMounts:
        - name: config
          mountPath: /app/config
        env:
        - name: SPRING_CONFIG_LOCATION
          value: classpath:/application.yml,/app/config/application.yml
      volumes:
      - name: config
        configMap:
          name: proxy-service-config
```

## API文档

### 代理转发端点

#### ACME协议转发

所有以`/acme/`开头的请求都会被自动转发到step-ca服务：

- **GET/POST/PUT/DELETE** `/acme/**`
  - 描述：转发ACME协议请求到step-ca
  - 请求：透明转发原始请求
  - 响应：透明返回step-ca的响应

#### 根证书获取

- **GET** `/roots.pem`
  - 描述：获取CA根证书
  - 响应：PEM格式的根证书

#### 健康检查

- **GET** `/health`
  - 描述：服务健康状态检查
  - 响应：`200 OK` 表示服务正常

### 证书管理端点

#### 生成证书

- **POST** `/api/certificates/generate`
  - 描述：手动生成新证书
  - 请求体：
    ```json
    {
      "commonName": "example.com",
      "subjectAlternativeNames": ["www.example.com", "api.example.com"],
      "validityDays": 365,
      "organization": "Example Corp",
      "organizationalUnit": "IT Department",
      "country": "US",
      "state": "California",
      "locality": "San Francisco",
      "keyType": "RSA",
      "keySize": 2048
    }
    ```
  - 响应：
    ```json
    {
      "status": "success",
      "serialNumber": "1234567890",
      "certificate": "-----BEGIN CERTIFICATE-----\n...",
      "privateKey": "-----BEGIN PRIVATE KEY-----\n...",
      "certificateChain": "-----BEGIN CERTIFICATE-----\n..."
    }
    ```

#### 注销证书

- **POST** `/api/certificates/revoke`
  - 描述：注销指定证书
  - 请求体：
    ```json
    {
      "serialNumber": "1234567890",
      "reason": "keyCompromise"
    }
    ```
  - 响应：
    ```json
    {
      "status": "success",
      "message": "Certificate revoked successfully"
    }
    ```

#### 下载证书

- **GET** `/api/certificates/download/{serialNumber}`
  - 描述：下载指定序列号的证书
  - 路径参数：`serialNumber` - 证书序列号
  - 响应：PEM格式的证书文件

#### 服务状态

- **GET** `/api/certificates/status`
  - 描述：证书管理服务状态
  - 响应：服务状态信息

## cert-manager集成

### 配置cert-manager使用代理服务

1. **创建ClusterIssuer**
   ```yaml
   apiVersion: cert-manager.io/v1
   kind: ClusterIssuer
   metadata:
     name: step-ca-issuer
   spec:
     acme:
       server: http://proxy-service:8080/acme/directory
       email: admin@example.com
       privateKeySecretRef:
         name: step-ca-issuer-key
       solvers:
       - http01:
           ingress:
             class: nginx
   ```

2. **创建Certificate资源**
   ```yaml
   apiVersion: cert-manager.io/v1
   kind: Certificate
   metadata:
     name: example-cert
     namespace: default
   spec:
     secretName: example-cert-tls
     issuerRef:
       name: step-ca-issuer
       kind: ClusterIssuer
     dnsNames:
     - example.com
     - www.example.com
   ```

### 网络策略配置

确保cert-manager可以访问proxy-service：

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-cert-manager-to-proxy
spec:
  podSelector:
    matchLabels:
      app: proxy-service
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: cert-manager
    ports:
    - protocol: TCP
      port: 8080
```

## 监控和日志

### 健康检查端点

服务提供了多个监控端点：

- `/actuator/health`：详细健康状态
- `/actuator/info`：应用信息
- `/actuator/metrics`：性能指标

### 日志配置

日志文件位于`logs/proxy-service.log`，包含以下信息：

- 请求转发日志
- 证书操作日志
- 错误和异常信息
- 性能指标

### Prometheus集成

可以通过添加micrometer依赖来支持Prometheus监控：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

## 故障排查

### 常见问题

1. **连接step-ca失败**
   - 检查step-ca服务地址配置
   - 验证网络连通性
   - 检查SSL证书配置

2. **cert-manager无法访问代理服务**
   - 验证Kubernetes网络策略
   - 检查Service和Ingress配置
   - 确认端口映射正确

3. **证书生成失败**
   - 检查step-ca服务状态
   - 验证ACME账户配置
   - 查看详细错误日志

### 调试模式

启用调试日志：

```yaml
logging:
  level:
    com.example.proxyservice: DEBUG
    org.apache.http: DEBUG
```

### 性能调优

1. **连接池配置**
   ```yaml
   proxy:
     max-connections: 200
     max-connections-per-route: 50
   ```

2. **JVM参数**
   ```bash
   java -Xmx512m -Xms256m -jar proxy-service.jar
   ```

## 安全考虑

### SSL/TLS配置

生产环境建议配置正确的SSL证书验证：

```yaml
proxy:
  trust-all-certs: false
```

### 访问控制

建议配置适当的网络策略和防火墙规则，限制对代理服务的访问。

### 敏感信息保护

使用Kubernetes Secret存储敏感配置信息：

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: proxy-service-secret
type: Opaque
data:
  step-ca-token: <base64-encoded-token>
```

## 开发指南

### 项目结构

```
proxy-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/proxyservice/
│   │   │       ├── ProxyServiceApplication.java
│   │   │       ├── config/
│   │   │       │   └── ProxyConfig.java
│   │   │       ├── controller/
│   │   │       │   ├── ProxyController.java
│   │   │       │   └── CertificateController.java
│   │   │       ├── service/
│   │   │       │   ├── ProxyService.java
│   │   │       │   └── CertificateService.java
│   │   │       └── model/
│   │   │           ├── CertificateRequest.java
│   │   │           ├── CertificateResponse.java
│   │   │           └── RevokeRequest.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/
├── pom.xml
└── README.md
```

### 扩展功能

1. **添加数据库支持**
   - 存储证书信息
   - 记录操作历史
   - 实现证书查询功能

2. **增强安全性**
   - 添加认证和授权
   - 实现API密钥管理
   - 支持mTLS

3. **提升可观测性**
   - 添加分布式追踪
   - 增强监控指标
   - 实现告警机制

## 许可证

本项目采用MIT许可证，详见LICENSE文件。

## 贡献指南

欢迎提交Issue和Pull Request来改进本项目。在提交代码前，请确保：

1. 代码符合项目编码规范
2. 添加适当的单元测试
3. 更新相关文档
4. 通过所有测试用例

## 联系方式

如有问题或建议，请通过以下方式联系：

- 提交GitHub Issue
- 发送邮件至：support@example.com

---

**作者**：Hello World  
**版本**：1.0.0  
**最后更新**：2024年12月

