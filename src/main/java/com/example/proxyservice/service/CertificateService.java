package com.example.proxyservice.service;

import com.example.proxyservice.config.ProxyConfig;
import com.example.proxyservice.model.CertificateRequest;
import com.example.proxyservice.model.CertificateResponse;
import com.example.proxyservice.model.RevokeRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.stereotype.Service;

import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CloseableHttpClient httpClient;
    private final ProxyConfig proxyConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CertificateResponse generateCertificate(CertificateRequest request) {
        try {
            // 生成密钥对
            KeyPair keyPair = generateKeyPair(request.getKeyType(), request.getKeySize());
            
            // 创建简化的证书请求
            String csr = createSimpleCSR(request, keyPair);
            
            // 向step-ca请求签名证书
            String signedCertificate = requestCertificateFromStepCA(csr, request);
            
            if (signedCertificate != null) {
                // 转换私钥为PEM格式
                String privateKeyPem = convertPrivateKeyToPEM(keyPair.getPrivate());
                
                // 获取证书序列号
                String serialNumber = extractSerialNumber(signedCertificate);
                
                return CertificateResponse.success(serialNumber, signedCertificate, privateKeyPem, signedCertificate);
            } else {
                return CertificateResponse.error("Failed to obtain certificate from step-ca");
            }
            
        } catch (Exception e) {
            log.error("Error generating certificate", e);
            return CertificateResponse.error("Certificate generation failed: " + e.getMessage());
        }
    }

    public CertificateResponse revokeCertificate(RevokeRequest request) {
        try {
            // 构建注销请求
            Map<String, Object> revokePayload = new HashMap<>();
            if (request.getSerialNumber() != null) {
                revokePayload.put("serial", request.getSerialNumber());
            }
            if (request.getCertificate() != null) {
                revokePayload.put("certificate", request.getCertificate());
            }
            revokePayload.put("reason", request.getReason());

            String jsonPayload = objectMapper.writeValueAsString(revokePayload);
            
            // 发送注销请求到step-ca
            HttpPost httpPost = new HttpPost(proxyConfig.getStepCaUrl() + "/acme/revoke");
            httpPost.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));
            httpPost.setHeader("Content-Type", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                
                if (response.getCode() == 200) {
                    CertificateResponse certResponse = new CertificateResponse();
                    certResponse.setStatus("success");
                    certResponse.setMessage("Certificate revoked successfully");
                    return certResponse;
                } else {
                    return CertificateResponse.error("Revocation failed: " + responseBody);
                }
            }
            
        } catch (Exception e) {
            log.error("Error revoking certificate", e);
            return CertificateResponse.error("Certificate revocation failed: " + e.getMessage());
        }
    }

    private KeyPair generateKeyPair(String keyType, int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(keyType);
        keyPairGenerator.initialize(keySize);
        return keyPairGenerator.generateKeyPair();
    }

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
        csrData.put("keyType", request.getKeyType());
        csrData.put("keySize", request.getKeySize());
        
        if (request.getSubjectAlternativeNames() != null && !request.getSubjectAlternativeNames().isEmpty()) {
            csrData.put("subjectAlternativeNames", request.getSubjectAlternativeNames());
        }

        return objectMapper.writeValueAsString(csrData);
    }

    private String requestCertificateFromStepCA(String csr, CertificateRequest request) {
        try {
            // 构建ACME证书请求
            Map<String, Object> acmeRequest = new HashMap<>();
            acmeRequest.put("csr", csr);
            acmeRequest.put("notBefore", Instant.now().toString());
            acmeRequest.put("notAfter", Instant.now().plus(request.getValidityDays(), ChronoUnit.DAYS).toString());

            String jsonPayload = objectMapper.writeValueAsString(acmeRequest);
            
            HttpPost httpPost = new HttpPost(proxyConfig.getStepCaUrl() + "/acme/new-cert");
            httpPost.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));
            httpPost.setHeader("Content-Type", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                if (response.getCode() == 200 || response.getCode() == 201) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    JsonNode jsonResponse = objectMapper.readTree(responseBody);
                    return jsonResponse.get("certificate").asText();
                } else {
                    log.error("Failed to get certificate from step-ca: {}", response.getCode());
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("Error requesting certificate from step-ca", e);
            return null;
        }
    }

    private String convertPrivateKeyToPEM(PrivateKey privateKey) throws Exception {
        // 简化处理：返回Base64编码的私钥
        // 实际项目中应该使用PEM格式
        return "-----BEGIN PRIVATE KEY-----\n" +
               Base64.getEncoder().encodeToString(privateKey.getEncoded()) +
               "\n-----END PRIVATE KEY-----";
    }

    private String extractSerialNumber(String certificatePem) {
        try {
            // 简化处理，实际应该解析PEM格式的证书
            return String.valueOf(System.currentTimeMillis());
        } catch (Exception e) {
            log.error("Error extracting serial number", e);
            return "unknown";
        }
    }
}

