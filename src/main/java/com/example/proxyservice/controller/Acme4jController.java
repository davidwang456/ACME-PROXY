package com.example.proxyservice.controller;

import com.example.proxyservice.model.CertificateRequest;
import com.example.proxyservice.model.CertificateResponse;
import com.example.proxyservice.model.RevokeRequest;
import com.example.proxyservice.service.Acme4jCertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/acme4j")
@RequiredArgsConstructor
public class Acme4jController {

    private final Acme4jCertificateService acme4jCertificateService;

    /**
     * 生成证书
     */
    @PostMapping("/certificates")
    public ResponseEntity<CertificateResponse> generateCertificate(@RequestBody CertificateRequest request) {
        log.info("Generating certificate for domains: {}", request.getSubjectAlternativeNames());
        CertificateResponse response = acme4jCertificateService.generateCertificate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 注销证书
     */
    @PostMapping("/certificates/revoke")
    public ResponseEntity<CertificateResponse> revokeCertificate(@RequestBody RevokeRequest request) {
        log.info("Revoking certificate with serial: {}", request.getSerialNumber());
        CertificateResponse response = acme4jCertificateService.revokeCertificate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 下载证书
     */
    @GetMapping("/certificates/{serialNumber}")
    public ResponseEntity<CertificateResponse> downloadCertificate(@PathVariable String serialNumber) {
        log.info("Downloading certificate with serial: {}", serialNumber);
        CertificateResponse response = acme4jCertificateService.downloadCertificate(serialNumber);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取账户信息
     */
    @GetMapping("/account")
    public ResponseEntity<Map<String, Object>> getAccountInfo() {
        log.info("Getting account info");
        Map<String, Object> info = acme4jCertificateService.getAccountInfo();
        return ResponseEntity.ok(info);
    }

    /**
     * 获取订单列表
     */
    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> getOrders() {
        log.info("Getting orders list");
        List<Map<String, Object>> orders = acme4jCertificateService.getOrders();
        return ResponseEntity.ok(orders);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy", "service", "acme4j"));
    }
} 