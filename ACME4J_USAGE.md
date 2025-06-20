# ACME4J 证书服务使用说明

## 概述

本项目集成了 ACME4J 组件，用于与 step-ca 进行 ACME 协议交互，实现证书的生成、注销和下载功能。

## 配置

### 1. 依赖配置

项目已包含以下 ACME4J 依赖：

```xml
<dependency>
    <groupId>org.shredzone.acme4j</groupId>
    <artifactId>acme4j-client</artifactId>
    <version>2.16</version>
</dependency>
<dependency>
    <groupId>org.shredzone.acme4j</groupId>
    <artifactId>acme4j-utils</artifactId>
    <version>2.16</version>
</dependency>
```

### 2. 应用配置

在 `application.yml` 中配置 ACME4J 相关参数：

```yaml
# Step-CA配置
step-ca:
  url: https://step.it.com

# ACME4J配置
acme:
  account:
    email: admin@example.com
    key:
      size: 2048
  challenge:
    timeout: 30000
    retry-count: 3
  certificate:
    default-validity-days: 90
```

## API 端点

### 1. 生成证书

**POST** `/acme4j/certificates`

请求体：
```json
{
  "commonName": "example.com",
  "subjectAlternativeNames": ["example.com", "www.example.com"],
  "organization": "Example Org",
  "organizationalUnit": "IT Department",
  "country": "CN",
  "state": "Beijing",
  "locality": "Beijing",
  "keyType": "RSA",
  "keySize": 2048,
  "validityDays": 90
}
```

响应：
```json
{
  "status": "success",
  "serialNumber": "1234567890",
  "certificate": "-----BEGIN CERTIFICATE-----\n...",
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...",
  "certificateChain": "-----BEGIN CERTIFICATE-----\n..."
}
```

### 2. 注销证书

**POST** `/acme4j/certificates/revoke`

请求体：
```json
{
  "serialNumber": "1234567890",
  "certificate": "-----BEGIN CERTIFICATE-----\n...",
  "reason": "UNSPECIFIED"
}
```

### 3. 下载证书

**GET** `/acme4j/certificates/{serialNumber}`

### 4. 获取账户信息

**GET** `/acme4j/account`

### 5. 获取订单列表

**GET** `/acme4j/orders`

### 6. 获取服务器目录信息

**GET** `/acme4j/directory`

### 7. 健康检查

**GET** `/acme4j/health`

## 使用示例

### 1. 生成证书

```bash
curl -X POST http://localhost:8080/acme4j/certificates \
  -H "Content-Type: application/json" \
  -d '{
    "commonName": "example.com",
    "subjectAlternativeNames": ["example.com", "www.example.com"],
    "organization": "Example Org",
    "keyType": "RSA",
    "keySize": 2048,
    "validityDays": 90
  }'
```

### 2. 注销证书

```bash
curl -X POST http://localhost:8080/acme4j/certificates/revoke \
  -H "Content-Type: application/json" \
  -d '{
    "serialNumber": "1234567890",
    "certificate": "-----BEGIN CERTIFICATE-----\n...",
    "reason": "UNSPECIFIED"
  }'
```

### 3. 获取账户信息

```bash
curl http://localhost:8080/acme4j/account
```

## 注意事项

### 1. HTTP-01 挑战

当前实现是模拟版本，实际使用时需要：

1. 将挑战响应部署到 web 服务器
2. 文件路径：`/.well-known/acme-challenge/{token}`
3. 确保该路径可以通过 HTTP 访问

### 2. 账户密钥持久化

当前实现每次都创建新的账户密钥，实际使用时应该：

1. 持久化存储账户密钥
2. 实现密钥的备份和恢复机制

### 3. 证书存储

当前实现没有持久化存储证书，实际使用时应该：

1. 实现证书的持久化存储
2. 实现证书的查询和管理功能

## 扩展功能

### 1. 实现真实的 ACME 协议交互

需要完善以下功能：

1. 真实的 HTTP-01 挑战处理
2. 账户密钥的持久化存储
3. 证书的持久化存储和管理
4. 错误处理和重试机制

### 2. 支持其他挑战类型

可以扩展支持：

1. DNS-01 挑战
2. TLS-ALPN-01 挑战

### 3. 证书续期

实现证书的自动续期功能：

1. 监控证书过期时间
2. 自动触发续期流程
3. 更新存储的证书

## 故障排除

### 1. 编译错误

如果遇到 ACME4J API 相关的编译错误，可能需要：

1. 检查 ACME4J 版本兼容性
2. 查看 ACME4J 官方文档
3. 使用正确的 API 调用方式

### 2. 连接错误

如果无法连接到 step-ca：

1. 检查 step-ca 服务是否正常运行
2. 检查网络连接
3. 检查 step-ca 的 ACME 端点配置

### 3. 挑战失败

如果 HTTP-01 挑战失败：

1. 检查挑战文件是否正确部署
2. 检查文件路径是否正确
3. 检查 web 服务器配置

## 相关链接

- [ACME4J 官方文档](https://shredzone.org/maven/acme4j/)
- [ACME 协议规范](https://tools.ietf.org/html/rfc8555)
- [Step-CA 文档](https://smallstep.com/docs/step-ca) 