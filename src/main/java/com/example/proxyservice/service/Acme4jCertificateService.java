package com.example.proxyservice.service;

import com.example.proxyservice.model.CertificateRequest;
import com.example.proxyservice.model.CertificateResponse;
import com.example.proxyservice.model.RevokeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class Acme4jCertificateService {

    @Value("${step-ca.url:https://step.it.com}")
    private String stepCaUrl;

    @Value("${acme.account.email:admin@example.com}")
    private String accountEmail;

    @Value("${acme.account.key.size:2048}")
    private int accountKeySize;

    @Value("${acme.challenge.timeout:30000}")
    private int challengeTimeout;

    @Value("${acme.challenge.retry-count:3}")
    private int challengeRetryCount;

    /**
     * 生成证书
     */
    public CertificateResponse generateCertificate(CertificateRequest request) {
        try {
            log.info("Generating certificate for domains: {}", request.getSubjectAlternativeNames());
            
            // 生成密钥对
            KeyPair domainKey = createKeyPair(request.getKeySize());
            
            // 创建简化的证书请求
            String csr = createSimpleCSR(request, domainKey);
            
            // 模拟向 step-ca 请求签名证书
            String signedCertificate = simulateCertificateRequest(csr, request);
            
            if (signedCertificate != null) {
                // 转换私钥为 PEM 格式
                String privateKeyPem = convertPrivateKeyToPEM(domainKey.getPrivate());
                
                // 获取证书序列号
                String serialNumber = extractSerialNumber(signedCertificate);
                
                return CertificateResponse.success(serialNumber, signedCertificate, privateKeyPem, signedCertificate);
            } else {
                return CertificateResponse.error("Failed to obtain certificate from step-ca");
            }
            
        } catch (Exception e) {
            log.error("Error generating certificate with ACME4J", e);
            return CertificateResponse.error("Certificate generation failed: " + e.getMessage());
        }
    }

    /**
     * 注销证书
     */
    public CertificateResponse revokeCertificate(RevokeRequest request) {
        try {
            log.info("Revoking certificate with serial: {}", request.getSerialNumber());
            
            // 模拟注销证书
            // 在实际实现中，这里应该调用 ACME4J 的注销 API
            
            CertificateResponse response = new CertificateResponse();
            response.setStatus("success");
            response.setMessage("Certificate revoked successfully (simulated)");
            return response;
            
        } catch (Exception e) {
            log.error("Error revoking certificate with ACME4J", e);
            return CertificateResponse.error("Certificate revocation failed: " + e.getMessage());
        }
    }

    /**
     * 下载证书
     */
    public CertificateResponse downloadCertificate(String serialNumber) {
        try {
            log.info("Downloading certificate with serial: {}", serialNumber);
            
            // 这里需要实现从存储中根据序列号查找证书
            // 或者通过 ACME 服务器查询证书状态
            // 暂时返回模拟响应
            
            CertificateResponse response = new CertificateResponse();
            response.setStatus("success");
            response.setMessage("Certificate download not implemented yet");
            return response;
            
        } catch (Exception e) {
            log.error("Error downloading certificate with ACME4J", e);
            return CertificateResponse.error("Certificate download failed: " + e.getMessage());
        }
    }

    /**
     * 创建密钥对
     */
    private KeyPair createKeyPair(int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(keySize);
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * 创建简化的证书请求
     */
    private String createSimpleCSR(CertificateRequest request, KeyPair keyPair) throws Exception {
        // 构建主题名称
        StringBuilder subjectBuilder = new StringBuilder();
        subjectBuilder.append("CN=").append(request.getCommonName());
        
        if (request.getOrganization() != null) {
            subjectBuilder.append(", O=").append(request.getOrganization());
        }
        if (request.getOrganizationalUnit() != null) {
            subjectBuilder.append(", OU=").append(request.getOrganizationalUnit());
        }
        if (request.getCountry() != null) {
            subjectBuilder.append(", C=").append(request.getCountry());
        }
        if (request.getState() != null) {
            subjectBuilder.append(", ST=").append(request.getState());
        }
        if (request.getLocality() != null) {
            subjectBuilder.append(", L=").append(request.getLocality());
        }

        // 简化处理：创建一个基本的CSR格式字符串
        // 实际项目中应该使用标准的PKCS#10 CSR格式
        Map<String, Object> csrData = new HashMap<>();
        csrData.put("subject", subjectBuilder.toString());
        csrData.put("publicKey", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        csrData.put("keyType", "RSA");
        csrData.put("keySize", request.getKeySize());
        
        if (request.getSubjectAlternativeNames() != null && !request.getSubjectAlternativeNames().isEmpty()) {
            csrData.put("subjectAlternativeNames", request.getSubjectAlternativeNames());
        }

        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(csrData);
    }

    /**
     * 模拟证书请求
     */
    private String simulateCertificateRequest(String csr, CertificateRequest request) {
        try {
            // 模拟向 step-ca 发送请求
            log.info("Simulating certificate request to step-ca");
            
            // 生成模拟的证书
            return generateMockCertificate(request);
            
        } catch (Exception e) {
            log.error("Error simulating certificate request", e);
            return null;
        }
    }

    /**
     * 生成模拟证书
     */
    private String generateMockCertificate(CertificateRequest request) {
        // 生成一个模拟的 PEM 格式证书
        return "-----BEGIN CERTIFICATE-----\n" +
               "MIIDXTCCAkWgAwIBAgIJAKoK8HhJhqXkMA0GCSqGSIb3DQEBCwUAMEUxCzAJ\n" +
               "BgNVBAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRl\n" +
               "cm5ldCBXaWRnaXRzIFB0eSBMdGQwHhcNMTkwMzI2MTIzNDU5WhcNMjAwMzI1\n" +
               "MTIzNDU5WjBFMQswCQYDVQQGEwJBVTETMBEGA1UECAwKU29tZS1TdGF0ZTEh\n" +
               "MB8GA1UECgwYSW50ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIIBIjANBgkqhkiG\n" +
               "9w0BAQEFAAOCAQ8AMIIBCgKCAQEA" + Base64.getEncoder().encodeToString(
                   ("Mock certificate for " + request.getCommonName()).getBytes()) + "\n" +
               "-----END CERTIFICATE-----";
    }

    /**
     * 转换私钥为 PEM 格式
     */
    private String convertPrivateKeyToPEM(PrivateKey privateKey) throws Exception {
        // 简化处理：返回Base64编码的私钥
        // 实际项目中应该使用PEM格式
        return "-----BEGIN PRIVATE KEY-----\n" +
               Base64.getEncoder().encodeToString(privateKey.getEncoded()) +
               "\n-----END PRIVATE KEY-----";
    }

    /**
     * 提取证书序列号
     */
    private String extractSerialNumber(String certificatePem) {
        try {
            // 简化处理，实际应该解析PEM格式的证书
            return String.valueOf(System.currentTimeMillis());
        } catch (Exception e) {
            log.error("Error extracting serial number", e);
            return "unknown";
        }
    }

    /**
     * 获取账户信息
     */
    public Map<String, Object> getAccountInfo() {
        try {
            Map<String, Object> info = new HashMap<>();
            info.put("accountUrl", stepCaUrl + "/acme/account");
            info.put("status", "active");
            info.put("email", accountEmail);
            info.put("keySize", accountKeySize);
            
            return info;
            
        } catch (Exception e) {
            log.error("Error getting account info", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 获取订单列表
     */
    public List<Map<String, Object>> getOrders() {
        try {
            // 返回模拟的订单列表
            List<Map<String, Object>> orders = new ArrayList<>();
            
            Map<String, Object> mockOrder = new HashMap<>();
            mockOrder.put("orderUrl", stepCaUrl + "/acme/order/123");
            mockOrder.put("status", "valid");
            mockOrder.put("domains", Arrays.asList("example.com", "www.example.com"));
            
            orders.add(mockOrder);
            
            return orders;
            
        } catch (Exception e) {
            log.error("Error getting orders", e);
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取 ACME 服务器目录信息
     */
    public Map<String, Object> getServerDirectory() {
        try {
            Map<String, Object> directory = new HashMap<>();
            directory.put("directory", stepCaUrl + "/acme/directory");
            directory.put("status", "available");
            directory.put("version", "2.0");
            return directory;
        } catch (Exception e) {
            log.error("Error getting server directory", e);
            return Map.of("error", e.getMessage());
        }
    }
} 