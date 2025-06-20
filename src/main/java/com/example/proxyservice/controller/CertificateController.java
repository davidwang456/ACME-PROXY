package com.example.proxyservice.controller;

import com.example.proxyservice.model.CertificateRequest;
import com.example.proxyservice.model.CertificateResponse;
import com.example.proxyservice.model.RevokeRequest;
import com.example.proxyservice.service.CertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @PostMapping("/generate")
    public ResponseEntity<CertificateResponse> generateCertificate(@Valid @RequestBody CertificateRequest request) {
        log.info("Generating certificate for CN: {}", request.getCommonName());
        CertificateResponse response = certificateService.generateCertificate(request);
        
        if ("success".equals(response.getStatus())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/revoke")
    public ResponseEntity<CertificateResponse> revokeCertificate(@Valid @RequestBody RevokeRequest request) {
        log.info("Revoking certificate with serial: {}", request.getSerialNumber());
        CertificateResponse response = certificateService.revokeCertificate(request);
        
        if ("success".equals(response.getStatus())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/download/{serialNumber}")
    public ResponseEntity<String> downloadCertificate(@PathVariable String serialNumber) {
        // 这里应该从数据库或存储中获取证书
        // 为了演示，返回一个示例响应
        log.info("Downloading certificate with serial: {}", serialNumber);
        
        String certificateContent = "-----BEGIN CERTIFICATE-----\n" +
                "示例证书内容，实际应该从存储中获取\n" +
                "-----END CERTIFICATE-----";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment", serialNumber + ".pem");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(certificateContent);
    }

    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        return ResponseEntity.ok("Certificate management service is running");
    }
}

