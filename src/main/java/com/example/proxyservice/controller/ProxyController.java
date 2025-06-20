package com.example.proxyservice.controller;

import com.example.proxyservice.service.ProxyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ProxyController {

    private final ProxyService proxyService;

    @RequestMapping(value = "/acme/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<byte[]> forwardAcmeRequest(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) throws IOException {
        
        log.info("Received {} request for path: {}", request.getMethod(), request.getRequestURI());
        return proxyService.forwardRequest(request, body);
    }

    @RequestMapping(value = "/roots.pem", method = RequestMethod.GET)
    public ResponseEntity<byte[]> forwardRootsRequest(HttpServletRequest request) throws IOException {
        log.info("Received request for roots.pem");
        return proxyService.forwardRequest(request, null);
    }

    @RequestMapping(value = "/health", method = RequestMethod.GET)
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}

