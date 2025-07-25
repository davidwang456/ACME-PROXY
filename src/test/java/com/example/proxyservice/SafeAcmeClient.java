package com.example.proxyservice;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.shredzone.acme4j.*;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.shredzone.acme4j.util.CSRBuilder;

import java.security.KeyPair;
import java.security.Security;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class SafeAcmeClient {
    public static void main(String[] args) {
        try {
            // 1. 添加安全提供程序
            Security.addProvider(new BouncyCastleProvider());
            
            // 2. 配置 SSL 以信任所有证书和主机名（仅用于测试）
            configureSSLTrustAll();
            
            // 3. 使用测试环境（推荐）
            Session session = new Session("https://localhost:9000");
            
            // 4. 创建账户
            KeyPair accountKey = KeyPairUtils.createKeyPair();
            Account account = new AccountBuilder()
                .addEmail("admin@itd.asky")
                .agreeToTermsOfService()
                .useKeyPair(accountKey)
                .create(session);
                
            System.out.println("账户创建成功: " + account.getLocation());

            // 5. 订购证书
            Order order = account.newOrder()
                .domains("example.com", "www.example.com")
                .create();

            // 6. 处理授权
            for (Authorization auth : order.getAuthorizations()) {
                System.out.println("处理域名授权: " + auth.getIdentifier().getDomain());
                
                // 查找 HTTP-01 挑战
                Http01Challenge challenge = auth.findChallenge(Http01Challenge.TYPE);
                if (challenge == null) {
                    throw new RuntimeException("未找到 HTTP-01 挑战");
                }
                
                // 获取挑战信息
                String token = challenge.getToken();
                String response = challenge.getAuthorization();
                
                System.out.println("挑战令牌: " + token);
                System.out.println("挑战响应: " + response);
                System.out.println("挑战 URL: http://" + auth.getIdentifier().getDomain() + "/.well-known/acme-challenge/" + token);
                
                // 在实际环境中，需要将挑战响应部署到 web 服务器
                // 这里简化处理，假设挑战已经完成
                System.out.println("注意：在实际环境中，需要将挑战响应部署到 web 服务器");
                
                // 触发挑战验证
                System.out.println("触发挑战验证...");
                challenge.trigger();
                
                // 等待挑战完成
                int challengeAttempts = 0;
                int maxChallengeAttempts = 5;
                
                while (challenge.getStatus() != Status.VALID && challengeAttempts < maxChallengeAttempts) {
                    if (challenge.getStatus() == Status.INVALID) {
                        String error = challenge.getError() != null ? challenge.getError().toString() : "Unknown error";
                        throw new RuntimeException("挑战失败: " + error);
                    }
                    
                    System.out.println("挑战状态: " + challenge.getStatus() + ", 等待中...");
                    Thread.sleep(2000L);
                    challenge.update();
                    challengeAttempts++;
                }
                
                if (challenge.getStatus() != Status.VALID) {
                    throw new RuntimeException("挑战超时，最终状态: " + challenge.getStatus());
                }
                
                System.out.println("域名 " + auth.getIdentifier().getDomain() + " 验证成功！");
            }

            // 7. 生成域名密钥对
            KeyPair domainKey = KeyPairUtils.createKeyPair(4096);
            
            // 8. 创建 CSR（证书签名请求）
            CSRBuilder csrb = new CSRBuilder();
            csrb.addDomains("example.com", "www.example.com");
            csrb.sign(domainKey);
            
            // 9. 执行订单（使用 CSR 字节数组）
            order.execute(csrb.getEncoded());
            System.out.println("订单已提交，等待证书生成...");

            // 10. 等待证书生成完成
            int maxAttempts = 10;
            int attempt = 0;
            
            while (order.getStatus() != Status.VALID && attempt < maxAttempts) {
                if (order.getStatus() == Status.INVALID) {
                    String error = order.getError() != null ? order.getError().toString() : "Unknown error";
                    throw new RuntimeException("证书订单失败: " + error);
                }
                
                System.out.println("订单状态: " + order.getStatus() + ", 等待中... (尝试 " + (attempt + 1) + "/" + maxAttempts + ")");
                
                // 等待 3 秒后再次检查
                Thread.sleep(3000L);
                
                // 更新订单状态
                order.update();
                attempt++;
            }
            
            if (order.getStatus() != Status.VALID) {
                throw new RuntimeException("证书生成超时，最终状态: " + order.getStatus());
            }

            // 11. 下载证书
            Certificate certificate = order.getCertificate();
            System.out.println("证书生成成功: " + certificate.getLocation());
            
            // 12. 获取证书详情
            System.out.println("证书序列号: " + certificate.getCertificate().getSerialNumber());
            System.out.println("证书主题: " + certificate.getCertificate().getSubjectDN());
            System.out.println("证书颁发者: " + certificate.getCertificate().getIssuerDN());
            System.out.println("证书有效期从: " + certificate.getCertificate().getNotBefore());
            System.out.println("证书有效期至: " + certificate.getCertificate().getNotAfter());
            
        } catch (Exception e) {
            System.err.println("ACME 操作失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 配置 SSL 以信任所有证书和主机名（仅用于测试环境）
     */
    private static void configureSSLTrustAll() throws Exception {
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
        
        // 创建信任所有主机名的验证器
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true; // 信任所有主机名
            }
        };
        
        // 设置默认主机名验证器
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        
        System.out.println("SSL 配置完成：已禁用证书和主机名验证（仅用于测试）");
    }
}