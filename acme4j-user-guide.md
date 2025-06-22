# acme4j 客户端使用手册

## 目录
1. [简介](#简介)
2. [快速开始](#快速开始)
3. [连接配置](#连接配置)
4. [账户管理](#账户管理)
5. [证书订购](#证书订购)
6. [域名授权](#域名授权)
7. [挑战验证](#挑战验证)
8. [证书管理](#证书管理)
9. [高级功能](#高级功能)
10. [错误处理](#错误处理)
11. [故障排除](#故障排除)
12. [完整示例](#完整示例)

## 简介

acme4j 是一个用于 [RFC 8555](https://tools.ietf.org/html/rfc8555) ACME 协议的 Java 客户端库。它可以帮助您自动化证书管理过程，包括域名验证和证书颁发。

### 主要特性

- 完全符合 RFC 8555 标准
- 支持 `http-01`、`dns-01` 和 `tls-alpn-01` 挑战类型
- 支持多种证书颁发机构（CA）
- 简单易用的 Java API
- 需要 JRE 17 或更高版本
- 支持 Let's Encrypt、Buypass、Google Trust Services 等 CA

### 依赖项

```xml
<dependency>
    <groupId>org.shredzone.acme4j</groupId>
    <artifactId>acme4j-client</artifactId>
    <version>3.5.0</version>
</dependency>
```

## 快速开始

### 基本设置

首先，添加 Bouncy Castle 安全提供程序：

```java
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.Security;

// 添加 Bouncy Castle 提供程序
Security.addProvider(new BouncyCastleProvider());
```

### 最简单的证书获取流程

```java
import org.shredzone.acme4j.*;

// 1. 创建会话
Session session = new Session("acme://letsencrypt.org/staging");

// 2. 创建或登录账户
KeyPair accountKey = KeyPairUtils.createKeyPair();
Account account = new AccountBuilder()
    .addEmail("admin@example.com")
    .agreeToTermsOfService()
    .useKeyPair(accountKey)
    .create(session);

// 3. 订购证书
Order order = account.newOrder()
    .domains("example.com", "www.example.com")
    .create();

// 4. 处理授权
for (Authorization auth : order.getAuthorizations()) {
    // 处理域名验证（见下文）
}

// 5. 生成证书
KeyPair domainKey = KeyPairUtils.createKeyPair(4096);
order.execute(domainKey);

// 6. 下载证书
Certificate certificate = order.getCertificate();
```

## 连接配置

### 支持的 CA

acme4j 支持多种证书颁发机构：

```java
// Let's Encrypt (生产环境)
Session session = new Session("acme://letsencrypt.org");

// Let's Encrypt (测试环境)
Session session = new Session("acme://letsencrypt.org/staging");

// Buypass
Session session = new Session("acme://buypass.com");

// Google Trust Services
Session session = new Session("acme://pki.goog");

// ZeroSSL
Session session = new Session("acme://zerossl.com");
```

### 自定义 CA

对于其他 ACME 兼容的 CA，可以使用标准 URL：

```java
// 使用标准 ACME 目录 URL
Session session = new Session("https://acme.example.com/directory");
```

### 网络设置

```java
Session session = new Session("acme://letsencrypt.org");

// 设置超时时间
session.networkSettings().setTimeout(Duration.ofSeconds(30));

// 设置代理
session.networkSettings().setProxySelector(proxySelector);

// 设置认证器
session.networkSettings().setAuthenticator(authenticator);

// 禁用压缩
session.networkSettings().setCompressionEnabled(false);
```

### 本地化设置

```java
// 设置语言（用于错误消息）
session.setLocale(Locale.CHINESE);
```

## 账户管理

### 创建新账户

```java
// 创建账户密钥对
KeyPair accountKey = KeyPairUtils.createKeyPair();

// 创建账户
Account account = new AccountBuilder()
    .addEmail("admin@example.com")
    .addContact("mailto:admin@example.com")
    .agreeToTermsOfService()
    .useKeyPair(accountKey)
    .create(session);

// 获取账户位置 URL（建议保存）
URL accountLocation = account.getLocation();
```

### 外部账户绑定 (EAB)

某些 CA 需要外部账户绑定：

```java
Account account = new AccountBuilder()
    .agreeToTermsOfService()
    .withKeyIdentifier("your-kid", "your-hmac-key")
    .useKeyPair(accountKey)
    .create(session);
```

### 登录现有账户

```java
// 使用账户位置 URL 登录
Login login = session.login(accountLocation, accountKey);
Account account = login.getAccount();

// 或者使用密钥对查找账户
Login login = new AccountBuilder()
    .onlyExisting()
    .agreeToTermsOfService()
    .useKeyPair(accountKey)
    .createLogin(session);
```

### 更新账户信息

```java
// 修改联系信息
account.modify()
    .addEmail("new-admin@example.com")
    .removeContact("mailto:old-admin@example.com")
    .commit();
```

### 更改账户密钥

```java
KeyPair newKeyPair = KeyPairUtils.createKeyPair();
account.changeKey(newKeyPair);
```

### 停用账户

```java
account.deactivate();
```

## 证书订购

### 基本证书订购

```java
// 创建订单
Order order = account.newOrder()
    .domains("example.com", "www.example.com")
    .create();

// 查看订单状态
Status status = order.getStatus(); // PENDING, READY, VALID, INVALID

// 查看标识符
List<Identifier> identifiers = order.getIdentifiers();
for (Identifier id : identifiers) {
    System.out.println("Domain: " + id.getDomain());
}
```

### 高级订单选项

```java
// 设置证书有效期
Instant notBefore = Instant.now();
Instant notAfter = Instant.now().plus(Duration.ofDays(90));

Order order = account.newOrder()
    .domains("example.com")
    .notBefore(notBefore)
    .notAfter(notAfter)
    .create();
```

### 自动续期 (STAR)

```java
// 启用自动续期
Order order = account.newOrder()
    .domains("example.com")
    .autoRenewal()
    .autoRenewalStart(Instant.now())
    .autoRenewalEnd(Instant.now().plus(Duration.ofDays(365)))
    .autoRenewalLifetime(Duration.ofDays(7))
    .create();
```

### 证书配置文件

```java
// 使用特定配置文件
Order order = account.newOrder()
    .domains("example.com")
    .profile("tlsserver")
    .create();
```

### 替换现有证书

```java
// 替换现有证书
Order order = account.newOrder()
    .domains("example.com")
    .replaces(existingCertificate)
    .create();
```

## 域名授权

### 处理授权

```java
// 获取所有授权
List<Authorization> authorizations = order.getAuthorizations();

for (Authorization auth : authorizations) {
    String domain = auth.getIdentifier().getDomain();
    Status status = auth.getStatus();
    
    if (status == Status.PENDING) {
        // 需要处理授权
        processAuthorization(auth);
    }
}
```

### 预授权域名

```java
// 预授权域名（不订购证书）
Authorization auth = account.preAuthorizeDomain("example.com");

// 或者使用标识符
Authorization auth = account.preAuthorize(Identifier.dns("example.com"));
```

### 子域名授权

```java
// 支持子域名授权
Authorization auth = account.preAuthorize(
    Identifier.dns("foo.bar.example.com").withAncestorDomain("example.com")
);
```

## 挑战验证

### HTTP-01 挑战

```java
public void processHttpChallenge(Authorization auth) throws AcmeException {
    // 查找 HTTP-01 挑战
    Http01Challenge challenge = auth.findChallenge(Http01Challenge.class)
        .orElseThrow(() -> new AcmeException("No HTTP-01 challenge available"));
    
    String domain = auth.getIdentifier().getDomain();
    String token = challenge.getToken();
    String content = challenge.getAuthorization();
    
    // 创建验证文件
    String path = "/.well-known/acme-challenge/" + token;
    System.out.println("Create file at: http://" + domain + path);
    System.out.println("Content: " + content);
    
    // 触发挑战
    challenge.trigger();
    
    // 等待验证完成
    Status status = challenge.waitForCompletion(Duration.ofSeconds(60));
    if (status != Status.VALID) {
        throw new AcmeException("HTTP challenge failed");
    }
}
```

### DNS-01 挑战

```java
public void processDnsChallenge(Authorization auth) throws AcmeException {
    // 查找 DNS-01 挑战
    Dns01Challenge challenge = auth.findChallenge(Dns01Challenge.class)
        .orElseThrow(() -> new AcmeException("No DNS-01 challenge available"));
    
    String domain = auth.getIdentifier().getDomain();
    String recordName = challenge.getRRName(domain);
    String digest = challenge.getDigest();
    
    // 创建 DNS TXT 记录
    System.out.println("Create DNS TXT record:");
    System.out.println(recordName + " IN TXT " + digest);
    
    // 触发挑战
    challenge.trigger();
    
    // 等待验证完成
    Status status = challenge.waitForCompletion(Duration.ofSeconds(60));
    if (status != Status.VALID) {
        throw new AcmeException("DNS challenge failed");
    }
}
```

### TLS-ALPN-01 挑战

```java
public void processTlsAlpnChallenge(Authorization auth) throws AcmeException {
    // 查找 TLS-ALPN-01 挑战
    TlsAlpn01Challenge challenge = auth.findChallenge(TlsAlpn01Challenge.class)
        .orElseThrow(() -> new AcmeException("No TLS-ALPN-01 challenge available"));
    
    String domain = auth.getIdentifier().getDomain();
    String token = challenge.getToken();
    
    // 配置 TLS 服务器以响应 acme-tls/1 协议
    System.out.println("Configure TLS server for domain: " + domain);
    System.out.println("Token: " + token);
    
    // 触发挑战
    challenge.trigger();
    
    // 等待验证完成
    Status status = challenge.waitForCompletion(Duration.ofSeconds(60));
    if (status != Status.VALID) {
        throw new AcmeException("TLS-ALPN challenge failed");
    }
}
```

### 挑战状态轮询

```java
public void waitForChallenge(Challenge challenge, Duration timeout) throws AcmeException {
    // 方法 1: 使用内置等待方法
    Status status = challenge.waitForCompletion(timeout);
    
    // 方法 2: 手动轮询
    Instant endTime = Instant.now().plus(timeout);
    while (Instant.now().isBefore(endTime)) {
        challenge.fetch();
        Status status = challenge.getStatus();
        
        if (status == Status.VALID) {
            return; // 成功
        } else if (status == Status.INVALID) {
            throw new AcmeException("Challenge failed: " + 
                challenge.getError().map(Problem::toString).orElse("unknown"));
        }
        
        Thread.sleep(2000); // 等待 2 秒
    }
    
    throw new AcmeException("Challenge timeout");
}
```

## 证书管理

### 生成证书

```java
// 等待订单准备就绪
order.waitUntilReady(Duration.ofSeconds(60));

// 生成域密钥对
KeyPair domainKey = KeyPairUtils.createKeyPair(4096);

// 执行订单（生成证书）
order.execute(domainKey);

// 等待订单完成
Status status = order.waitForCompletion(Duration.ofSeconds(60));
if (status != Status.VALID) {
    throw new AcmeException("Order failed: " + 
        order.getError().map(Problem::toString).orElse("unknown"));
}
```

### 自定义 CSR

```java
// 使用自定义 CSR 构建器
order.execute(domainKey, csr -> {
    csr.setOrganization("ACME Corp.");
    csr.setOrganizationUnit("IT Department");
    csr.setCountry("CN");
    csr.setState("Beijing");
    csr.setLocality("Beijing");
});
```

### 下载证书

```java
// 获取证书对象
Certificate certificate = order.getCertificate();

// 获取证书链
X509Certificate cert = certificate.getCertificate();
List<X509Certificate> chain = certificate.getCertificateChain();

// 保存到文件
try (FileWriter fw = new FileWriter("certificate.crt")) {
    certificate.writeCertificate(fw);
}

// 获取证书位置 URL
URL certLocation = certificate.getLocation();
```

### 证书续期

```java
// 检查续期信息
Optional<RenewalInfo> renewalInfo = certificate.getRenewalInfo();
if (renewalInfo.isPresent()) {
    RenewalInfo info = renewalInfo.get();
    System.out.println("Renewal unique ID: " + info.getRenewalUniqueId());
    System.out.println("Suggested window start: " + info.getSuggestedWindowStart());
    System.out.println("Suggested window end: " + info.getSuggestedWindowEnd());
}
```

### 证书撤销

```java
// 撤销证书
certificate.revoke(RevocationReason.KEY_COMPROMISE);
```

## 高级功能

### 密钥对管理

```java
import org.shredzone.acme4j.util.KeyPairUtils;

// 生成 RSA 密钥对
KeyPair rsaKey = KeyPairUtils.createKeyPair(2048);

// 生成 EC 密钥对
KeyPair ecKey = KeyPairUtils.createECKeyPair("secp256r1");

// 保存密钥对
try (FileWriter fw = new FileWriter("key.pem")) {
    KeyPairUtils.writeKeyPair(keyPair, fw);
}

// 加载密钥对
try (FileReader fr = new FileReader("key.pem")) {
    KeyPair keyPair = KeyPairUtils.readKeyPair(fr);
}
```

### 订单列表

```java
// 获取账户的所有订单
Iterator<Order> orders = account.getOrders();
while (orders.hasNext()) {
    Order order = orders.next();
    System.out.println("Order: " + order.getLocation());
    System.out.println("Status: " + order.getStatus());
}
```

### 元数据查询

```java
// 获取 CA 元数据
Metadata metadata = session.getMetadata();

// 检查功能支持
if (metadata.isExternalAccountRequired()) {
    System.out.println("External Account Binding required");
}

if (metadata.isAutoRenewalEnabled()) {
    System.out.println("Auto-renewal supported");
}

// 获取服务条款
Optional<URI> tos = metadata.getTermsOfService();
if (tos.isPresent()) {
    System.out.println("Terms of Service: " + tos.get());
}
```

### 错误处理

```java
try {
    // ACME 操作
    order.execute(domainKey);
} catch (AcmeServerException e) {
    // 服务器错误
    System.err.println("Server error: " + e.getProblem().getDetail());
} catch (AcmeNetworkException e) {
    // 网络错误
    System.err.println("Network error: " + e.getMessage());
} catch (AcmeProtocolException e) {
    // 协议错误
    System.err.println("Protocol error: " + e.getMessage());
} catch (AcmeException e) {
    // 其他 ACME 错误
    System.err.println("ACME error: " + e.getMessage());
}
```

## 错误处理

### 常见错误类型

```java
// 服务器错误
AcmeServerException - CA 服务器返回的错误
AcmeNetworkException - 网络连接问题
AcmeProtocolException - 协议相关问题
AcmeNotSupportedException - 不支持的功能
AcmeLazyLoadingException - 延迟加载错误
```

### 错误详情

```java
try {
    order.execute(domainKey);
} catch (AcmeServerException e) {
    Problem problem = e.getProblem();
    System.err.println("Type: " + problem.getType());
    System.err.println("Detail: " + problem.getDetail());
    System.err.println("Status: " + problem.getStatus());
    
    // 检查是否需要重试
    if (problem.getStatus() >= 500) {
        // 服务器错误，可以重试
    }
}
```

### 重试机制

```java
public void executeWithRetry(Runnable operation, int maxRetries) {
    for (int i = 0; i < maxRetries; i++) {
        try {
            operation.run();
            return; // 成功
        } catch (AcmeServerException e) {
            if (e.getProblem().getStatus() >= 500 && i < maxRetries - 1) {
                // 服务器错误，等待后重试
                try {
                    Thread.sleep(1000 * (i + 1)); // 指数退避
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            } else {
                throw e; // 重新抛出
            }
        }
    }
}
```

## 故障排除

### SSL 证书验证问题

#### 问题描述
```
javax.net.ssl.SSLHandshakeException: PKIX path building failed: 
sun.security.provider.certpath.SunCertPathBuilderException: 
unable to find valid certification path to requested target
```

这个错误通常发生在以下情况：
- 使用自签名证书的测试 CA
- 企业内网环境中的代理服务器
- 证书链不完整
- 系统时间不准确

#### 解决方案

##### 方案 1: 使用测试环境
```java
// 使用 Let's Encrypt 测试环境（推荐）
Session session = new Session("acme://letsencrypt.org/staging");

// 或使用其他测试 CA
Session session = new Session("acme://buypass.com/staging");
```

##### 方案 2: 自定义 SSL 上下文（仅用于测试）
```java
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

// 创建信任所有证书的 SSL 上下文（仅用于测试环境）
public static SSLContext createTrustAllSSLContext() throws Exception {
    TrustManager[] trustAllCerts = new TrustManager[] {
        new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return null; }
            public void checkClientTrusted(X509Certificate[] certs, String authType) { }
            public void checkServerTrusted(X509Certificate[] certs, String authType) { }
        }
    };

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
    
    return sslContext;
}

// 使用自定义 SSL 上下文
SSLContext sslContext = createTrustAllSSLContext();
HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

// 然后创建会话
Session session = new Session("acme://your-ca.com");
```

##### 方案 3: 添加自定义证书到信任库
```java
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public static void addCertificateToTrustStore(String certPath) throws Exception {
    // 加载证书文件
    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    X509Certificate cert = (X509Certificate) cf.generateCertificate(
        new FileInputStream(certPath));
    
    // 获取默认信任库
    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    String trustStorePath = System.getProperty("javax.net.ssl.trustStore");
    String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
    
    if (trustStorePath != null) {
        trustStore.load(new FileInputStream(trustStorePath), 
            trustStorePassword != null ? trustStorePassword.toCharArray() : null);
    } else {
        trustStore.load(null, null);
    }
    
    // 添加证书
    trustStore.setCertificateEntry("custom-ca", cert);
    
    // 设置系统属性
    System.setProperty("javax.net.ssl.trustStore", "custom-truststore.jks");
    System.setProperty("javax.net.ssl.trustStorePassword", "password");
    
    // 保存信任库
    try (FileOutputStream fos = new FileOutputStream("custom-truststore.jks")) {
        trustStore.store(fos, "password".toCharArray());
    }
}
```

##### 方案 4: 使用系统属性（仅用于测试）
```java
// 在创建 Session 之前设置系统属性
System.setProperty("com.sun.net.ssl.checkRevocation", "false");
System.setProperty("sun.security.ssl.allowUnsafeRenegotiation", "true");

// 或者完全禁用证书验证（仅用于测试）
System.setProperty("javax.net.ssl.trustStore", "NONE");
```

##### 方案 5: 检查系统时间
```java
// 检查系统时间是否正确
System.out.println("Current time: " + new java.util.Date());
System.out.println("System timezone: " + System.getProperty("user.timezone"));

// 如果时间不准确，请同步系统时间
```

#### 推荐的解决方案

**对于生产环境：**
1. 使用官方支持的 CA（如 Let's Encrypt）
2. 确保系统时间准确
3. 更新 Java 版本到最新版本
4. 确保网络连接正常

**对于测试环境：**
1. 使用测试 CA（如 Let's Encrypt staging）
2. 如果必须使用自签名证书，使用方案 2 或 3

#### 完整示例：处理 SSL 问题

```java
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.shredzone.acme4j.*;
import javax.net.ssl.*;
import java.security.Security;
import java.security.cert.X509Certificate;

public class SSLTroubleshootingExample {
    
    public static void main(String[] args) {
        try {
            // 1. 添加 Bouncy Castle 提供程序
            Security.addProvider(new BouncyCastleProvider());
            
            // 2. 检查系统时间
            System.out.println("System time: " + new java.util.Date());
            
            // 3. 尝试不同的连接方式
            try {
                // 首先尝试标准连接
                createSessionStandard();
            } catch (Exception e) {
                System.out.println("Standard connection failed: " + e.getMessage());
                
                if (e.getCause() instanceof SSLHandshakeException) {
                    System.out.println("SSL issue detected, trying alternative approaches...");
                    
                    // 尝试测试环境
                    try {
                        createSessionStaging();
                    } catch (Exception e2) {
                        System.out.println("Staging connection also failed: " + e2.getMessage());
                        
                        // 最后尝试信任所有证书（仅用于测试）
                        createSessionTrustAll();
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("All connection attempts failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void createSessionStandard() throws Exception {
        System.out.println("Trying standard connection...");
        Session session = new Session("acme://letsencrypt.org");
        System.out.println("Standard connection successful!");
    }
    
    private static void createSessionStaging() throws Exception {
        System.out.println("Trying staging connection...");
        Session session = new Session("acme://letsencrypt.org/staging");
        System.out.println("Staging connection successful!");
    }
    
    private static void createSessionTrustAll() throws Exception {
        System.out.println("Trying trust-all connection (for testing only)...");
        
        // 创建信任所有证书的 SSL 上下文
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                public void checkServerTrusted(X509Certificate[] certs, String authType) { }
            }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        
        Session session = new Session("acme://letsencrypt.org");
        System.out.println("Trust-all connection successful!");
    }
}
```

#### 调试 SSL 连接

```java
// 启用 SSL 调试（仅用于调试）
System.setProperty("javax.net.debug", "ssl,handshake");

// 或者更详细的调试
System.setProperty("javax.net.debug", "ssl,handshake,data,trustmanager");
```

#### 检查网络连接

```java
import java.net.HttpURLConnection;
import java.net.URL;

public static boolean testConnection(String urlString) {
    try {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        int responseCode = connection.getResponseCode();
        System.out.println("Connection test result: " + responseCode);
        
        return responseCode == 200;
    } catch (Exception e) {
        System.err.println("Connection test failed: " + e.getMessage());
        return false;
    }
}

// 测试连接
testConnection("https://acme-v02.api.letsencrypt.org/directory");
```

### 其他常见问题

#### 1. 网络连接问题
```java
// 设置代理
System.setProperty("http.proxyHost", "proxy.company.com");
System.setProperty("http.proxyPort", "8080");
System.setProperty("https.proxyHost", "proxy.company.com");
System.setProperty("https.proxyPort", "8080");

// 或者使用 Session 的网络设置
Session session = new Session("acme://letsencrypt.org");
session.networkSettings().setTimeout(Duration.ofSeconds(30));
```

#### 2. 内存不足问题
```java
// 增加 JVM 内存
// java -Xmx2g -jar your-app.jar

// 或者在代码中优化内存使用
System.gc(); // 手动触发垃圾回收
```

#### 3. 权限问题
```java
// 检查文件权限
File keyFile = new File("account.key");
if (!keyFile.canRead()) {
    System.err.println("Cannot read key file: " + keyFile.getAbsolutePath());
}

if (!keyFile.canWrite()) {
    System.err.println("Cannot write to key file: " + keyFile.getAbsolutePath());
}
```

---

## 完整示例

### 完整的证书获取流程

```java
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.shredzone.acme4j.*;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.KeyPair;
import java.security.Security;
import java.time.Duration;

public class CertificateManager {
    
    private static final String CA_URI = "acme://letsencrypt.org/staging";
    private static final String ACCOUNT_EMAIL = "admin@example.com";
    private static final File ACCOUNT_KEY_FILE = new File("account.key");
    private static final File DOMAIN_KEY_FILE = new File("domain.key");
    private static final File CERTIFICATE_FILE = new File("certificate.crt");
    
    public static void main(String[] args) {
        try {
            // 初始化安全提供程序
            Security.addProvider(new BouncyCastleProvider());
            
            // 获取证书
            CertificateManager manager = new CertificateManager();
            manager.fetchCertificate("example.com", "www.example.com");
            
        } catch (Exception e) {
            System.err.println("Failed to get certificate: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void fetchCertificate(String... domains) throws Exception {
        // 1. 创建会话
        Session session = new Session(CA_URI);
        
        // 2. 加载或创建账户
        KeyPair accountKey = loadOrCreateAccountKey();
        Account account = findOrRegisterAccount(session, accountKey);
        
        // 3. 加载或创建域密钥
        KeyPair domainKey = loadOrCreateDomainKey();
        
        // 4. 创建订单
        Order order = account.newOrder().domains(domains).create();
        
        // 5. 处理授权
        for (Authorization auth : order.getAuthorizations()) {
            authorize(auth);
        }
        
        // 6. 等待订单准备就绪
        order.waitUntilReady(Duration.ofSeconds(60));
        
        // 7. 生成证书
        order.execute(domainKey);
        
        // 8. 等待订单完成
        Status status = order.waitForCompletion(Duration.ofSeconds(60));
        if (status != Status.VALID) {
            throw new AcmeException("Order failed: " + 
                order.getError().map(Problem::toString).orElse("unknown"));
        }
        
        // 9. 下载并保存证书
        Certificate certificate = order.getCertificate();
        try (FileWriter fw = new FileWriter(CERTIFICATE_FILE)) {
            certificate.writeCertificate(fw);
        }
        
        System.out.println("Certificate generated successfully!");
        System.out.println("Certificate file: " + CERTIFICATE_FILE.getAbsolutePath());
    }
    
    private KeyPair loadOrCreateAccountKey() throws Exception {
        if (ACCOUNT_KEY_FILE.exists()) {
            try (FileReader fr = new FileReader(ACCOUNT_KEY_FILE)) {
                return KeyPairUtils.readKeyPair(fr);
            }
        } else {
            KeyPair keyPair = KeyPairUtils.createKeyPair();
            try (FileWriter fw = new FileWriter(ACCOUNT_KEY_FILE)) {
                KeyPairUtils.writeKeyPair(keyPair, fw);
            }
            return keyPair;
        }
    }
    
    private KeyPair loadOrCreateDomainKey() throws Exception {
        if (DOMAIN_KEY_FILE.exists()) {
            try (FileReader fr = new FileReader(DOMAIN_KEY_FILE)) {
                return KeyPairUtils.readKeyPair(fr);
            }
        } else {
            KeyPair keyPair = KeyPairUtils.createKeyPair(4096);
            try (FileWriter fw = new FileWriter(DOMAIN_KEY_FILE)) {
                KeyPairUtils.writeKeyPair(keyPair, fw);
            }
            return keyPair;
        }
    }
    
    private Account findOrRegisterAccount(Session session, KeyPair accountKey) throws AcmeException {
        // 检查服务条款
        Optional<URI> tos = session.getMetadata().getTermsOfService();
        if (tos.isPresent()) {
            System.out.println("Please accept Terms of Service: " + tos.get());
            // 在实际应用中，应该让用户确认
        }
        
        // 创建账户
        Account account = new AccountBuilder()
            .addEmail(ACCOUNT_EMAIL)
            .agreeToTermsOfService()
            .useKeyPair(accountKey)
            .create(session);
            
        System.out.println("Account created: " + account.getLocation());
        return account;
    }
    
    private void authorize(Authorization auth) throws AcmeException {
        String domain = auth.getIdentifier().getDomain();
        System.out.println("Authorizing domain: " + domain);
        
        // 如果已经有效，跳过
        if (auth.getStatus() == Status.VALID) {
            return;
        }
        
        // 查找 HTTP-01 挑战
        Http01Challenge challenge = auth.findChallenge(Http01Challenge.class)
            .orElseThrow(() -> new AcmeException("No HTTP-01 challenge available"));
        
        // 显示挑战信息
        String token = challenge.getToken();
        String content = challenge.getAuthorization();
        System.out.println("HTTP Challenge:");
        System.out.println("URL: http://" + domain + "/.well-known/acme-challenge/" + token);
        System.out.println("Content: " + content);
        
        // 在实际应用中，这里应该自动创建文件
        System.out.println("Please create the challenge file and press Enter...");
        try {
            System.in.read();
        } catch (Exception e) {
            throw new AcmeException("User cancelled", e);
        }
        
        // 触发挑战
        challenge.trigger();
        
        // 等待验证完成
        Status status = challenge.waitForCompletion(Duration.ofSeconds(60));
        if (status != Status.VALID) {
            throw new AcmeException("Challenge failed for domain: " + domain);
        }
        
        System.out.println("Authorization completed for domain: " + domain);
    }
}

### 生产环境建议

1. **密钥安全**: 妥善保管账户密钥对，这是访问账户的唯一方式
2. **错误处理**: 实现完善的错误处理和重试机制
3. **日志记录**: 记录所有操作和错误信息
4. **监控**: 监控证书有效期，提前续期
5. **备份**: 定期备份账户密钥和证书
6. **测试**: 使用测试环境验证流程
7. **自动化**: 实现自动化的证书管理流程

### 最佳实践

1. **使用测试环境**: 在开发阶段使用测试 CA
2. **合理设置超时**: 根据网络情况调整超时时间
3. **批量处理**: 对于多个域名，考虑批量处理
4. **缓存机制**: 缓存目录信息和元数据
5. **并发控制**: 避免过多的并发请求
6. **资源清理**: 及时清理临时文件和资源

---

这个使用手册涵盖了 acme4j 的主要功能和使用方法。建议在实际使用前先在测试环境中验证流程，并根据具体需求调整配置和实现。

## SSL 证书验证问题解决方案

### 问题描述
在使用 acme4j 连接某些 CA 时，可能会遇到以下 SSL 证书验证错误：

```
javax.net.ssl.SSLHandshakeException: PKIX path building failed: 
sun.security.provider.certpath.SunCertPathBuilderException: 
unable to find valid certification path to requested target
```

### 常见原因
1. **自签名证书**: 测试 CA 使用自签名证书
2. **证书链不完整**: CA 的证书链缺少中间证书
3. **系统时间不准确**: 证书验证时系统时间与证书有效期不匹配
4. **企业代理**: 企业网络环境中的代理服务器证书问题
5. **本地 CA**: 使用本地部署的 step-ca 等 CA 服务

### 解决方案

#### 方案 1: 使用测试环境（推荐）
```java
// 使用 Let's Encrypt 测试环境
Session session = new Session("acme://letsencrypt.org/staging");

// 使用 Buypass 测试环境
Session session = new Session("acme://buypass.com/staging");
```

#### 方案 2: 信任所有证书（仅用于测试）
```java
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class SSLQuickFix {
    
    public static void trustAllCertificates() throws Exception {
        // 创建信任所有证书的 TrustManager
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                public void checkServerTrusted(X509Certificate[] certs, String authType) { }
            }
        };

        // 创建 SSL 上下文
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        
        // 设置默认 SSL 套接字工厂
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        
        // 禁用主机名验证
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        
        System.out.println("SSL 证书验证已禁用（仅用于测试）");
    }
    
    public static void main(String[] args) {
        try {
            // 在创建 Session 之前调用
            trustAllCertificates();
            
            // 现在可以安全地创建会话
            Session session = new Session("https://your-ca.com/directory");
            System.out.println("连接成功！");
            
        } catch (Exception e) {
            System.err.println("连接失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

#### 方案 3: 添加自定义证书到信任库
```java
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class CertificateTrustManager {
    
    public static void addCertificateToTrustStore(String certPath, String alias) throws Exception {
        // 加载证书文件
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
            new FileInputStream(certPath));
        
        // 获取默认信任库
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        String trustStorePath = System.getProperty("javax.net.ssl.trustStore");
        String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
        
        if (trustStorePath != null) {
            trustStore.load(new FileInputStream(trustStorePath), 
                trustStorePassword != null ? trustStorePassword.toCharArray() : null);
        } else {
            trustStore.load(null, null);
        }
        
        // 添加证书
        trustStore.setCertificateEntry(alias, cert);
        
        // 创建自定义 TrustManager
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        
        // 设置 SSL 上下文
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        
        System.out.println("已添加证书到信任库: " + alias);
    }
}
```

#### 方案 4: 使用系统属性（仅用于测试）
```java
public class SystemPropertySSLFix {
    
    public static void disableSSLVerification() {
        // 禁用证书撤销检查
        System.setProperty("com.sun.net.ssl.checkRevocation", "false");
        
        // 允许不安全的重新协商
        System.setProperty("sun.security.ssl.allowUnsafeRenegotiation", "true");
        
        // 禁用主机名验证
        System.setProperty("jdk.tls.allowUnsafeServerCertChange", "true");
        
        System.out.println("SSL 验证已通过系统属性禁用（仅用于测试）");
    }
}
```

### 针对 step-ca 的特殊处理

#### 使用 HTTP 端口
如果 step-ca 同时提供 HTTP 和 HTTPS 端口，建议使用 HTTP 端口：

```java
// 使用 HTTP 端口（9001）而不是 HTTPS 端口（9000）
Session session = new Session("http://localhost:9001/directory");
```

#### 添加 step-ca 根证书
```java
public class StepCAConnector {
    
    public static void setupStepCAConnection() throws Exception {
        // 方法 1: 使用 step-ca 的根证书
        String stepCARootCert = "path/to/step-ca-root.crt";
        CertificateTrustManager.addCertificateToTrustStore(stepCARootCert, "step-ca-root");
        
        // 方法 2: 获取 step-ca 的根证书
        // step ca health --root step-ca-root.crt
        
        // 然后创建会话
        Session session = new Session("https://step-ca.id.com/directory");
    }
}
```

### 调试 SSL 连接

#### 启用 SSL 调试
```java
public class SSLDebugger {
    
    public static void enableSSLDebug() {
        // 启用 SSL 调试
        System.setProperty("javax.net.debug", "ssl,handshake");
        
        // 更详细的调试信息
        // System.setProperty("javax.net.debug", "ssl,handshake,data,trustmanager");
        
        System.out.println("SSL 调试已启用");
    }
    
    public static void testConnection(String url) {
        try {
            URL testUrl = new URL(url);
            HttpsURLConnection connection = (HttpsURLConnection) testUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            System.out.println("连接测试结果: " + responseCode);
            
        } catch (Exception e) {
            System.err.println("连接测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### 完整的问题诊断和解决示例

```java
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.shredzone.acme4j.*;
import javax.net.ssl.*;
import java.security.Security;
import java.security.cert.X509Certificate;

public class SSLQuickFix {
    
    public static void main(String[] args) {
        try {
            // 1. 初始化安全提供程序
            Security.addProvider(new BouncyCastleProvider());
            
            // 2. 检查系统时间
            System.out.println("系统时间: " + new java.util.Date());
            
            // 3. 尝试不同的连接方式
            String[] testUrls = {
                "acme://letsencrypt.org/staging",
                "acme://letsencrypt.org",
                "https://your-ca.com/directory"
            };
            
            for (String url : testUrls) {
                try {
                    System.out.println("\n尝试连接: " + url);
                    createSession(url);
                    System.out.println("连接成功: " + url);
                    break; // 成功则退出循环
                    
                } catch (Exception e) {
                    System.out.println("连接失败: " + e.getMessage());
                    
                    if (e.getCause() instanceof SSLHandshakeException) {
                        System.out.println("检测到 SSL 问题，尝试修复...");
                        try {
                            trustAllCertificates();
                            createSession(url);
                            System.out.println("SSL 修复后连接成功: " + url);
                            break;
                        } catch (Exception e2) {
                            System.out.println("SSL 修复后仍然失败: " + e2.getMessage());
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("所有连接尝试都失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void createSession(String url) throws Exception {
        Session session = new Session(url);
        // 测试连接
        session.getMetadata();
    }
    
    private static void trustAllCertificates() throws Exception {
        // 创建信任所有证书的 TrustManager
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                public void checkServerTrusted(X509Certificate[] certs, String authType) { }
            }
        };

        // 创建 SSL 上下文
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        
        // 设置默认 SSL 套接字工厂
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        
        // 禁用主机名验证
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        
        System.out.println("SSL 证书验证已禁用（仅用于测试）");
    }
}
```

### 生产环境建议

1. **优先使用官方 CA**: 如 Let's Encrypt、Buypass 等
2. **确保系统时间准确**: 使用 NTP 服务同步时间
3. **更新 Java 版本**: 使用最新的 Java 版本以获得更好的 SSL 支持
4. **检查网络连接**: 确保网络连接正常，没有代理干扰
5. **使用测试环境**: 在开发阶段使用测试 CA 验证流程

### 常见错误及解决方案

| 错误类型 | 可能原因 | 解决方案 |
|---------|---------|---------|
| `PKIX path building failed` | 证书链不完整 | 使用测试环境或添加根证书 |
| `No subject alternative DNS name matching` | 证书 SAN 不匹配 | 禁用主机名验证或使用正确域名 |
| `Certificate signature algorithm disabled` | 算法被禁用 | 更新 Java 版本或使用支持的算法 |
| `Connection timeout` | 网络问题 | 检查网络连接和防火墙设置 |
| `SSL handshake timeout` | 网络延迟 | 增加超时时间或检查网络质量 |

---

这个 SSL 问题解决方案部分提供了针对不同场景的详细处理方法。建议根据具体环境选择合适的解决方案，并在生产环境中谨慎使用信任所有证书的方法。

这个使用手册涵盖了 acme4j 的主要功能和使用方法。建议在实际使用前先在测试环境中验证流程，并根据具体需求调整配置和实现。 