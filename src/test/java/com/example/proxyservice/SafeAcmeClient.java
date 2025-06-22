package com.example.acmeproxy;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.shredzone.acme4j.*;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.security.KeyPair;
import java.security.Security;

public class SafeAcmeClient {
    public static void main(String[] args) {
        try {
            // 1. 添加安全提供程序
            Security.addProvider(new BouncyCastleProvider());
            
            // 2. 使用测试环境（推荐）
            Session session = new Session("https://localhost:9000");
            
            // 3. 创建账户
            KeyPair accountKey = KeyPairUtils.createKeyPair();
            Account account = new AccountBuilder()
                .addEmail("admin@itd.asky")
                .agreeToTermsOfService()
                .useKeyPair(accountKey)
                .create(session);
                
            System.out.println("账户创建成功: " + account.getLocation());
            
        } catch (Exception e) {
            System.err.println("创建账户失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}