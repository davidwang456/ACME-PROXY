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

        // 获取 Content-Type
        String contentType = request.getContentType();
        ContentType httpContentType = parseContentType(contentType);

        // 创建HTTP请求
        HttpUriRequestBase httpRequest = createHttpRequest(method, targetUrl, body, httpContentType);
        
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

    /**
     * 解析 Content-Type 字符串为 ContentType 对象
     */
    private ContentType parseContentType(String contentTypeStr) {
        if (contentTypeStr == null || contentTypeStr.trim().isEmpty()) {
            return ContentType.APPLICATION_JSON;
        }
        
        // 先清理 Content-Type 字符串，移除通配符
        String sanitizedContentType = sanitizeContentType(contentTypeStr);
        if (sanitizedContentType == null) {
            return ContentType.APPLICATION_JSON;
        }
        
        try {
            // 使用更安全的方式创建 ContentType
            return createContentTypeSafely(sanitizedContentType);
        } catch (Exception e) {
            log.warn("Failed to parse Content-Type: {}, using default", sanitizedContentType, e);
            return ContentType.APPLICATION_JSON;
        }
    }

    /**
     * 安全地创建 ContentType 对象
     */
    private ContentType createContentTypeSafely(String contentTypeStr) {
        if (contentTypeStr == null || contentTypeStr.trim().isEmpty()) {
            return ContentType.APPLICATION_JSON;
        }
        
        String trimmed = contentTypeStr.trim();
        
        // 使用预定义的 ContentType 常量
        if ("application/json".equals(trimmed)) {
            return ContentType.APPLICATION_JSON;
        } else if ("application/xml".equals(trimmed)) {
            return ContentType.APPLICATION_XML;
        } else if ("text/plain".equals(trimmed)) {
            return ContentType.TEXT_PLAIN;
        } else if ("text/html".equals(trimmed)) {
            return ContentType.TEXT_HTML;
        } else if ("application/octet-stream".equals(trimmed)) {
            return ContentType.APPLICATION_OCTET_STREAM;
        } else if ("application/x-www-form-urlencoded".equals(trimmed)) {
            return ContentType.APPLICATION_FORM_URLENCODED;
        } else if ("multipart/form-data".equals(trimmed)) {
            return ContentType.MULTIPART_FORM_DATA;
        } else if (trimmed.startsWith("application/")) {
            // 对于其他 application 类型，尝试解析
            try {
                return ContentType.parse(trimmed);
            } catch (Exception e) {
                log.debug("Failed to parse application Content-Type: {}, using application/json", trimmed);
                return ContentType.APPLICATION_JSON;
            }
        } else if (trimmed.startsWith("text/")) {
            // 对于其他 text 类型，尝试解析
            try {
                return ContentType.parse(trimmed);
            } catch (Exception e) {
                log.debug("Failed to parse text Content-Type: {}, using text/plain", trimmed);
                return ContentType.TEXT_PLAIN;
            }
        } else {
            // 对于其他类型，尝试解析，失败则使用默认值
            try {
                return ContentType.parse(trimmed);
            } catch (Exception e) {
                log.debug("Failed to parse Content-Type: {}, using application/octet-stream", trimmed);
                return ContentType.APPLICATION_OCTET_STREAM;
            }
        }
    }

    private HttpUriRequestBase createHttpRequest(String method, String url, byte[] body, ContentType contentType) {
        HttpUriRequestBase request;
        
        switch (method.toUpperCase()) {
            case "GET":
                request = new HttpGet(url);
                break;
            case "POST":
                request = new HttpPost(url);
                if (body != null && body.length > 0) {
                    ((HttpPost) request).setEntity(new ByteArrayEntity(body, contentType));
                }
                break;
            case "PUT":
                request = new HttpPut(url);
                if (body != null && body.length > 0) {
                    ((HttpPut) request).setEntity(new ByteArrayEntity(body, contentType));
                }
                break;
            case "DELETE":
                request = new HttpDelete(url);
                break;
            case "PATCH":
                request = new HttpPatch(url);
                if (body != null && body.length > 0) {
                    ((HttpPatch) request).setEntity(new ByteArrayEntity(body, contentType));
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
            
            log.debug("Processing header: {} = {}", headerName, headerValue);
            
            // 跳过一些不应该转发的头
            if (!shouldSkipHeader(headerName)) {
                // 特殊处理 Content-Type 头，避免通配符子类型
                if ("content-type".equals(headerName.toLowerCase())) {
                    String sanitizedContentType = sanitizeContentType(headerValue);
                    if (sanitizedContentType != null) {
                        log.debug("Setting sanitized Content-Type: {} -> {}", headerValue, sanitizedContentType);
                        httpRequest.setHeader(headerName, sanitizedContentType);
                    } else {
                        log.debug("Skipping null Content-Type: {}", headerValue);
                    }
                } else {
                    httpRequest.setHeader(headerName, headerValue);
                }
            } else {
                log.debug("Skipping header: {}", headerName);
            }
        }
    }

    /**
     * 清理 Content-Type 头，移除通配符子类型
     */
    private String sanitizeContentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return null;
        }
        
        String trimmedContentType = contentType.trim();
        
        // 移除通配符子类型，使用默认的 application/json
        if (trimmedContentType.contains("*/*") || trimmedContentType.contains("*/")) {
            log.debug("Replacing wildcard Content-Type '{}' with application/json", trimmedContentType);
            return "application/json";
        }
        
        // 如果包含通配符，使用具体的类型
        if (trimmedContentType.contains("*")) {
            // 根据主类型返回具体的子类型
            if (trimmedContentType.startsWith("application/")) {
                log.debug("Replacing wildcard application Content-Type '{}' with application/json", trimmedContentType);
                return "application/json";
            } else if (trimmedContentType.startsWith("text/")) {
                log.debug("Replacing wildcard text Content-Type '{}' with text/plain", trimmedContentType);
                return "text/plain";
            } else if (trimmedContentType.startsWith("image/")) {
                log.debug("Replacing wildcard image Content-Type '{}' with image/png", trimmedContentType);
                return "image/png";
            } else {
                log.debug("Replacing wildcard Content-Type '{}' with application/octet-stream", trimmedContentType);
                return "application/octet-stream";
            }
        }
        
        // 检查是否是有效的 Content-Type 格式
        if (!trimmedContentType.contains("/")) {
            log.debug("Invalid Content-Type format '{}', using application/json", trimmedContentType);
            return "application/json";
        }
        
        return trimmedContentType;
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

