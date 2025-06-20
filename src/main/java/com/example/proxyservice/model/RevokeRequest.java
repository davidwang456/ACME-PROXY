package com.example.proxyservice.model;

import lombok.Data;

@Data
public class RevokeRequest {
    
    private String serialNumber;
    
    private String certificate;
    
    private String reason = "unspecified";
}

