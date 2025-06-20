package com.example.proxyservice.service;

import com.example.proxyservice.config.ProxyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyService {

    private final CloseableHttpClient httpClient;
    private final ProxyConfig proxyConfig;

    public ResponseEntity<byte[]> forwardRequest(HttpServletRequest request, byte[] body) throws IOException {
        String method = request.getMethod();
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        
        // 构建目标URL
        String targetUrl = proxyConfig.getStepCaUrl() + requestUri;
        if (queryString != null) {
            targetUrl += "?" + queryString;
        }

        log.info("Forwarding {} request to: {}", method, targetUrl);

        // 创建HTTP请求
        HttpUriRequestBase httpRequest = createHttpRequest(method, targetUrl, body);
        
        // 复制请求头
        copyRequestHeaders(request, httpRequest);

        try (CloseableHttpResponse response = httpClient.execute(httpRequest)) {
            // 获取响应体
            byte[] responseBody = EntityUtils.toByteArray(response.getEntity());
            
            // 构建响应头
            HttpHeaders responseHeaders = new HttpHeaders();
            for (Header header : response.getHeaders()) {
                String headerName = header.getName();
                // 跳过一些不应该转发的头
                if (!shouldSkipHeader(headerName)) {
                    responseHeaders.add(headerName, header.getValue());
                }
            }

            log.info("Response status: {}, body size: {} bytes", 
                    response.getCode(), responseBody.length);

            return ResponseEntity.status(response.getCode())
                    .headers(responseHeaders)
                    .body(responseBody);
        }
    }

    private HttpUriRequestBase createHttpRequest(String method, String url, byte[] body) {
        HttpUriRequestBase request;
        
        switch (method.toUpperCase()) {
            case "GET":
                request = new HttpGet(url);
                break;
            case "POST":
                request = new HttpPost(url);
                if (body != null && body.length > 0) {
                    ((HttpPost) request).setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_JSON));
                }
                break;
            case "PUT":
                request = new HttpPut(url);
                if (body != null && body.length > 0) {
                    ((HttpPut) request).setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_JSON));
                }
                break;
            case "DELETE":
                request = new HttpDelete(url);
                break;
            case "PATCH":
                request = new HttpPatch(url);
                if (body != null && body.length > 0) {
                    ((HttpPatch) request).setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_JSON));
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        
        return request;
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpUriRequestBase httpRequest) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            
            // 跳过一些不应该转发的头
            if (!shouldSkipHeader(headerName)) {
                httpRequest.setHeader(headerName, headerValue);
            }
        }
    }

    private boolean shouldSkipHeader(String headerName) {
        String lowerCaseName = headerName.toLowerCase();
        return lowerCaseName.equals("host") ||
               lowerCaseName.equals("connection") ||
               lowerCaseName.equals("transfer-encoding") ||
               lowerCaseName.equals("content-length") ||
               lowerCaseName.startsWith("proxy-");
    }
}

