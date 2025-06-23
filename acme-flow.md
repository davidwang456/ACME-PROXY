# ACME 协议流程详解

## 概述

ACME（Automated Certificate Management Environment）是一个用于自动化证书管理的协议，主要用于 Let's Encrypt 等 CA 的证书颁发。RFC 8555 定义了 ACME 协议的标准。

## ACME 协议完整流程

### 1. 发现阶段（Discovery）

客户端首先需要获取 ACME 服务器的目录信息，了解可用的端点。

```
客户端 → ACME 服务器
GET /directory
```

**响应示例：**
```json
{
  "newNonce": "https://acme.example.com/acme/new-nonce",
  "newAccount": "https://acme.example.com/acme/new-account", 
  "newOrder": "https://acme.example.com/acme/new-order",
  "revokeCert": "https://acme.example.com/acme/revoke-cert",
  "keyChange": "https://acme.example.com/acme/key-change"
}
```

### 2. 账户创建（Account Creation）

客户端需要创建一个账户来与 ACME 服务器交互。

```
客户端 → ACME 服务器
POST /acme/new-account
```

**请求体：**
```json
{
  "protected": {
    "alg": "RS256",
    "jwk": { 
      "kty": "RSA",
      "n": "base64url(公钥模数)",
      "e": "base64url(公钥指数)"
    },
    "nonce": "nonce值",
    "url": "https://acme.example.com/acme/new-account"
  },
  "payload": {
    "contact": ["mailto:admin@example.com"],
    "termsOfServiceAgreed": true
  },
  "signature": "base64url(签名)"
}
```

**响应：**
```json
{
  "status": "valid",
  "contact": ["mailto:admin@example.com"],
  "orders": "https://acme.example.com/acme/orders/account123"
}
```

### 3. 订单创建（Order Creation）

客户端创建证书订单，指定需要的域名。

```
客户端 → ACME 服务器
POST /acme/new-order
```

**请求体：**
```json
{
  "protected": { /* JWS 头部 */ },
  "payload": {
    "identifiers": [
      {"type": "dns", "value": "example.com"},
      {"type": "dns", "value": "www.example.com"}
    ],
    "notBefore": "2023-01-01T00:00:00Z",
    "notAfter": "2023-04-01T00:00:00Z"
  },
  "signature": "base64url(签名)"
}
```

**响应：**
```json
{
  "status": "pending",
  "expires": "2023-01-08T00:00:00Z",
  "identifiers": [
    {"type": "dns", "value": "example.com"},
    {"type": "dns", "value": "www.example.com"}
  ],
  "authorizations": [
    "https://acme.example.com/acme/authz/1234",
    "https://acme.example.com/acme/authz/5678"
  ],
  "finalize": "https://acme.example.com/acme/order/1234/finalize"
}
```

### 4. 域名验证（Domain Validation）

#### 4.1 获取授权信息

对于每个域名，客户端需要获取授权信息。

```
客户端 → ACME 服务器
POST /acme/authz/1234
```

**响应：**
```json
{
  "identifier": {"type": "dns", "value": "example.com"},
  "status": "pending",
  "expires": "2023-01-08T00:00:00Z",
  "challenges": [
    {
      "type": "http-01",
      "url": "https://acme.example.com/acme/challenge/1234/5678",
      "token": "token值",
      "status": "pending"
    },
    {
      "type": "dns-01",
      "url": "https://acme.example.com/acme/challenge/1234/5679",
      "token": "token值",
      "status": "pending"
    }
  ]
}
```

#### 4.2 完成挑战（Challenge Completion）

##### HTTP-01 挑战

1. **计算挑战响应：**
   ```
   keyAuthorization = token + "." + base64url(thumbprint(accountKey))
   ```

2. **部署挑战响应：**
   - 文件路径：`http://example.com/.well-known/acme-challenge/{token}`
   - 文件内容：`keyAuthorization`

3. **通知服务器验证：**
   ```
   客户端 → ACME 服务器
   POST /acme/challenge/1234/5678
   ```

   **请求体：**
   ```json
   {
     "protected": { /* JWS 头部 */ },
     "payload": "",
     "signature": "base64url(签名)"
   }
   ```

##### DNS-01 挑战

1. **计算挑战响应：**
   ```
   keyAuthorization = base64url(thumbprint(accountKey))
   ```

2. **创建 DNS TXT 记录：**
   - 记录名：`_acme-challenge.example.com`
   - 记录值：`keyAuthorization`

3. **通知服务器验证：**
   ```
   客户端 → ACME 服务器
   POST /acme/challenge/1234/5679
   ```

#### 4.3 轮询挑战状态

```
客户端 → ACME 服务器
POST /acme/challenge/1234/5678
```

**响应：**
```json
{
  "type": "http-01",
  "url": "https://acme.example.com/acme/challenge/1234/5678",
  "status": "valid",
  "validated": "2023-01-01T12:00:00Z"
}
```

### 5. 证书签名请求（CSR）

#### 5.1 生成 CSR

客户端生成包含域名信息的 PKCS#10 CSR：

```java
CSRBuilder csrb = new CSRBuilder();
csrb.addDomains("example.com", "www.example.com");
csrb.setOrganization("Example Org");
csrb.setOrganizationalUnit("IT Department");
csrb.setCountry("CN");
csrb.setState("Beijing");
csrb.setLocality("Beijing");
csrb.sign(domainKey);
```

#### 5.2 提交 CSR

```
客户端 → ACME 服务器
POST /acme/order/1234/finalize
```

**请求体：**
```json
{
  "protected": { /* JWS 头部 */ },
  "payload": {
    "csr": "base64url(CSR)"
  },
  "signature": "base64url(签名)"
}
```

**响应：**
```json
{
  "status": "processing",
  "expires": "2023-01-08T00:00:00Z",
  "identifiers": [...],
  "authorizations": [...],
  "finalize": "https://acme.example.com/acme/order/1234/finalize"
}
```

### 6. 证书生成和下载

#### 6.1 轮询订单状态

```
客户端 → ACME 服务器
POST /acme/order/1234
```

**响应：**
```json
{
  "status": "valid",
  "expires": "2023-01-08T00:00:00Z",
  "identifiers": [...],
  "authorizations": [...],
  "finalize": "https://acme.example.com/acme/order/1234/finalize",
  "certificate": "https://acme.example.com/acme/cert/abcd1234"
}
```

#### 6.2 下载证书

```
客户端 → ACME 服务器
POST /acme/cert/abcd1234
```

**响应：** PEM 格式的证书

```
-----BEGIN CERTIFICATE-----
MIIDXTCCAkWgAwIBAgIJAKoK8HhJhqXkMA0GCSqGSIb3DQEBCwUAMEUxCzAJ
BgNVBAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRl
cm5ldCBXaWRnaXRzIFB0eSBMdGQwHhcNMTkwMzI2MTIzNDU5WhcNMjAwMzI1
MTIzNDU5WjBFMQswCQYDVQQGEwJBVTETMBEGA1UECAwKU29tZS1TdGF0ZTEh
MB8GA1UECgwYSW50ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIIBIjANBgkqhkiG
9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
-----END CERTIFICATE-----
```

## 挑战类型详解

### 1. HTTP-01 挑战

**验证方式：** HTTP 文件访问

**步骤：**
1. 服务器生成随机 token
2. 客户端计算：`keyAuthorization = token + "." + base64url(thumbprint(accountKey))`
3. 客户端将 `keyAuthorization` 保存到：`http://{domain}/.well-known/acme-challenge/{token}`
4. 服务器访问该 URL 验证内容

**优点：**
- 简单易实现
- 不需要特殊配置

**缺点：**
- 需要 web 服务器可访问
- 可能被防火墙阻止

### 2. DNS-01 挑战

**验证方式：** DNS TXT 记录

**步骤：**
1. 服务器生成随机 token
2. 客户端计算：`keyAuthorization = base64url(thumbprint(accountKey))`
3. 客户端创建 DNS TXT 记录：`_acme-challenge.{domain} IN TXT {keyAuthorization}`
4. 服务器查询 DNS 记录验证

**优点：**
- 不需要 web 服务器
- 适用于内网域名
- 更安全

**缺点：**
- 需要 DNS 管理权限
- 传播时间较长

### 3. TLS-ALPN-01 挑战

**验证方式：** TLS 握手

**步骤：**
1. 服务器生成随机 token
2. 客户端计算证书指纹
3. 客户端在 TLS 握手中提供 `acme-tls/1` 协议
4. 服务器通过 TLS 握手验证

**优点：**
- 不需要额外的 HTTP 或 DNS 配置
- 安全性高

**缺点：**
- 实现复杂
- 需要 TLS 服务器支持

## 安全机制

### 1. JWS（JSON Web Signature）

所有 ACME 请求都使用 JWS 签名，确保：
- **完整性：** 防止数据被篡改
- **真实性：** 确保请求来源可信
- **不可否认性：** 防止发送者否认

**JWS 结构：**
```json
{
  "protected": {
    "alg": "RS256",
    "jwk": { /* 公钥信息 */ },
    "nonce": "nonce值",
    "url": "请求URL"
  },
  "payload": "base64url(请求体)",
  "signature": "base64url(签名)"
}
```

### 2. Nonce 机制

**作用：** 防止重放攻击

**流程：**
1. 客户端请求 nonce：`GET /acme/new-nonce`
2. 服务器返回 nonce 值
3. 客户端在 JWS 中使用该 nonce
4. 服务器验证 nonce 有效性

### 3. 账户密钥

**作用：** 证明账户身份

**要求：**
- 使用强加密算法（如 RSA-2048）
- 安全存储私钥
- 定期轮换

## 错误处理

### 常见错误类型

| 错误类型 | 描述 | 解决方案 |
|---------|------|----------|
| `badNonce` | 无效的 nonce | 重新获取 nonce |
| `badSignature` | 签名错误 | 检查签名算法和密钥 |
| `unauthorized` | 未授权 | 检查账户状态 |
| `connection` | 连接错误 | 检查网络连接 |
| `dns` | DNS 错误 | 检查 DNS 配置 |
| `tls` | TLS 错误 | 检查证书配置 |

### 错误响应格式

```json
{
  "type": "urn:ietf:params:acme:error:badNonce",
  "detail": "Invalid nonce in JWS header",
  "status": 400
}
```

## 实际应用示例

### Java 实现示例

```java
// 1. 创建会话（发现阶段）
Session session = new Session("https://localhost:9000");

// 2. 创建账户
KeyPair accountKey = KeyPairUtils.createKeyPair(2048);
Account account = new AccountBuilder()
    .addEmail("admin@example.com")
    .agreeToTermsOfService()
    .useKeyPair(accountKey)
    .create(session);

// 3. 创建订单
Order order = account.newOrder()
    .domains("example.com", "www.example.com")
    .create();

// 4. 处理域名验证
for (Authorization auth : order.getAuthorizations()) {
    Http01Challenge challenge = auth.findChallenge(Http01Challenge.TYPE);
    
    // 获取挑战信息
    String token = challenge.getToken();
    String response = challenge.getAuthorization();
    
    // 部署挑战响应（实际应用中需要实现）
    deployChallengeResponse(auth.getIdentifier().getDomain(), token, response);
    
    // 触发验证
    challenge.trigger();
    
    // 等待验证完成
    while (challenge.getStatus() != Status.VALID) {
        Thread.sleep(2000L);
        challenge.update();
    }
}

// 5. 生成并提交 CSR
KeyPair domainKey = KeyPairUtils.createKeyPair(2048);
CSRBuilder csrb = new CSRBuilder();
csrb.addDomains("example.com", "www.example.com");
csrb.sign(domainKey);
order.execute(csrb.getEncoded());

// 6. 等待证书生成
while (order.getStatus() != Status.VALID) {
    Thread.sleep(3000L);
    order.update();
}

// 7. 下载证书
Certificate certificate = order.getCertificate();
```

### 挑战响应部署示例

```java
private void deployChallengeResponse(String domain, String token, String response) {
    // HTTP-01 挑战部署
    String path = "/.well-known/acme-challenge/" + token;
    String content = response;
    
    // 实际应用中需要将内容保存到 web 服务器
    // 这里只是示例
    System.out.println("需要将以下内容保存到: http://" + domain + path);
    System.out.println("内容: " + content);
}
```

## 最佳实践

### 1. 安全性
- 使用强加密算法（RSA-2048 或 ECDSA-P256）
- 安全存储私钥
- 定期轮换密钥
- 使用 HTTPS 通信

### 2. 可靠性
- 实现重试机制
- 监控证书过期时间
- 自动续期证书
- 备份重要数据

### 3. 性能
- 缓存 nonce 值
- 批量处理多个域名
- 异步处理长时间操作
- 优化网络请求

### 4. 监控
- 记录所有操作日志
- 监控证书状态
- 设置告警机制
- 定期检查系统健康

## 相关标准

- **RFC 8555**: ACME 协议规范
- **RFC 7231**: HTTP/1.1 语义和内容
- **RFC 7515**: JSON Web Signature (JWS)
- **RFC 7517**: JSON Web Key (JWK)
- **RFC 5280**: X.509 证书格式

## 总结

ACME 协议通过标准化的流程实现了证书颁发的自动化，主要特点包括：

1. **自动化：** 无需人工干预
2. **标准化：** 基于 RFC 标准
3. **安全性：** 多重安全机制
4. **灵活性：** 支持多种挑战类型
5. **可扩展性：** 支持多种 CA 实现

这个协议已经成为现代 PKI 系统的重要组成部分，为互联网的安全通信提供了重要支撑。 