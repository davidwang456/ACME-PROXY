package com.example.proxyservice.model;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
public class CertificateRequest {
    
    @NotBlank(message = "Common name is required")
    private String commonName;
    
    private List<String> subjectAlternativeNames;
    
    @NotNull(message = "Validity days is required")
    private Integer validityDays = 365;
    
    private String organization;
    
    private String organizationalUnit;
    
    private String country;
    
    private String state;
    
    private String locality;
    
    private String keyType = "RSA";
    
    private Integer keySize = 2048;
}

