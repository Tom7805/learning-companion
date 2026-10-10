package com.learningcompanion.privacy.api;

import com.learningcompanion.privacy.api.dto.TermsVersionResponse;
import com.learningcompanion.privacy.application.TermsVersionService;
import com.learningcompanion.shared.web.ApiPaths;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.LEGAL)
public class TermsController {

    private final TermsVersionService termsVersionService;

    public TermsController(TermsVersionService termsVersionService) {
        this.termsVersionService = termsVersionService;
    }

    @GetMapping("/current")
    public TermsVersionResponse current() {
        return TermsVersionResponse.of(termsVersionService.current());
    }
}
