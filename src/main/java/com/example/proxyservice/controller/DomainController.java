package com.certmanager.controller;

import com.certmanager.model.Domain;
import com.certmanager.repository.DomainRepository;
import com.certmanager.service.CertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/domains")
public class DomainController {
    
    @Autowired
    private DomainRepository domainRepository;
    
    @Autowired
    private CertificateService certificateService;
    
    // 处理模板视图的方法
    @GetMapping
    public String getAllDomainsView(Model model) {
        List<Domain> domains = domainRepository.findAll();
        model.addAttribute("domains", domains);
        model.addAttribute("newDomain", new Domain());
        return "domains/all";
    }
    
    // 处理表单提交
    @PostMapping
    public String registerDomainForm(@ModelAttribute Domain newDomain, RedirectAttributes redirectAttributes) {
        try {
            // 检查域名是否已存在
            if (domainRepository.existsByDomainName(newDomain.getDomainName())) {
                redirectAttributes.addFlashAttribute("error", "域名已存在: " + newDomain.getDomainName());
                return "redirect:/domains";
            }
            
            // 设置域名类型
            newDomain.setDomainType(Domain.determineDomainType(newDomain.getDomainName()));
            newDomain.setActive(true);
            
            // 先申请证书，如果成功再保存域名
            String result = certificateService.requestCertificate(newDomain);
            
            // 检查证书申请是否成功
            if (result.contains("成功")) {
                // 证书申请成功，保存域名（证书服务内部已经保存了）
                redirectAttributes.addFlashAttribute("success", "域名注册成功: " + newDomain.getDomainName() + " - " + result);
            } else {
                // 证书申请失败，不保存域名
                redirectAttributes.addFlashAttribute("error", "域名注册失败: " + result);
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "域名注册失败: " + e.getMessage());
        }
        
        return "redirect:/domains";
    }
    
    // 删除域名
    @GetMapping("/{id}")
    public String deleteDomain(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            if (!domainRepository.existsById(id)) {
                redirectAttributes.addFlashAttribute("error", "域名不存在");
                return "redirect:/domains";
            }
            
            domainRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "域名删除成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "域名删除失败: " + e.getMessage());
        }
        
        return "redirect:/domains";
    }
    
    // 下载证书
    @GetMapping("/download-cert/{id}")
    public ResponseEntity<String> downloadCertificate(@PathVariable Long id) {
        Optional<Domain> domainOpt = domainRepository.findById(id);
        
        if (domainOpt.isEmpty() || domainOpt.get().getCertificate() == null) {
            return ResponseEntity.notFound().build();
        }
        
        Domain domain = domainOpt.get();
        
        // 设置下载头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", domain.getDomainName() + ".pem");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(domain.getCertificate());
    }
    
    // REST API方法 - 返回JSON数据
    @GetMapping("/api")
    @ResponseBody
    public List<Domain> getAllDomains() {
        return domainRepository.findAll();
    }
    
    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Domain> getDomainById(@PathVariable Long id) {
        Optional<Domain> domain = domainRepository.findById(id);
        return domain.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<String> registerDomainApi(@RequestBody Domain domain) {
        // 检查域名是否已存在
        if (domainRepository.existsByDomainName(domain.getDomainName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("域名已存在: " + domain.getDomainName());
        }
        
        String result = certificateService.requestCertificate(domain);
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteDomainApi(@PathVariable Long id) {
        if (!domainRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        
        domainRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}