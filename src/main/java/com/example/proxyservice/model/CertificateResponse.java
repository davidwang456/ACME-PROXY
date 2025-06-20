package com.example.proxyservice.model;

import lombok.Data;

@Data
public class CertificateResponse {
    
    private String serialNumber;
    
    private String certificate;
    
    private String privateKey;
    
    private String certificateChain;
    
    private String status;
    
    private String message;
    
    private String issuedAt;
    
    private String expiresAt;
    
    public static CertificateResponse success(String serialNumber, String certificate, String privateKey, String certificateChain) {
        CertificateResponse response = new CertificateResponse();
        response.setSerialNumber(serialNumber);
        response.setCertificate(certificate);
        response.setPrivateKey(privateKey);
        response.setCertificateChain(certificateChain);
        response.setStatus("success");
        return response;
    }
    
    public static CertificateResponse error(String message) {
        CertificateResponse response = new CertificateResponse();
        response.setStatus("error");
        response.setMessage(message);
        return response;
    }
}

