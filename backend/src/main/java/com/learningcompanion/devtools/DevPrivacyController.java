package com.learningcompanion.devtools;

import com.learningcompanion.privacy.api.dto.TermsVersionResponse;
import com.learningcompanion.privacy.application.ProductNewsMailer;
import com.learningcompanion.privacy.application.TermsVersionService;
import com.learningcompanion.privacy.domain.TermsVersion;
import com.learningcompanion.shared.web.ApiPaths;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Công cụ thử cho NCL-01-CN-006: đăng một phiên bản điều khoản mới (để thử hộp thoại đồng ý lại) và gửi
 * một bản tin tính năng mới (để thử việc chỉ gửi cho người đang đồng ý). Không bao giờ bật ở môi trường thật.
 */
@RestController
@RequestMapping(ApiPaths.DEV)
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevPrivacyController {

    private final TermsVersionService terms;
    private final ProductNewsMailer news;
    private final Clock clock;

    public DevPrivacyController(TermsVersionService terms, ProductNewsMailer news, Clock clock) {
        this.terms = terms;
        this.news = news;
        this.clock = clock;
    }

    /**
     * Phiên bản có hiệu lực sau {@code effectiveInSeconds} giây theo đồng hồ của máy chủ. Kiểm thử đặt mốc rất xa
     * rồi tua đồng hồ tới đó, nên phiên bản thử không bao giờ có hiệu lực với dữ liệu thật trên máy phát triển.
     */
    @PostMapping("/legal/versions")
    public TermsVersionResponse.Document publish(@RequestBody PublishRequest request) {
        return TermsVersionResponse.Document.of(terms.publish(request.documentType(), request.version(),
                request.summary(), request.highlights() == null ? List.of() : request.highlights(),
                request.changes() == null ? List.of() : request.changes(),
                clock.instant().plus(Duration.ofSeconds(Math.max(0, request.effectiveInSeconds())))));
    }

    @PostMapping("/product-news")
    public Sent sendProductNews(@RequestBody NewsRequest request) {
        return new Sent(news.send(request.headline(), request.body()));
    }

    public record PublishRequest(TermsVersion.DocumentType documentType, String version, String summary,
                                 List<String> highlights, List<String> changes, long effectiveInSeconds) {
    }

    public record NewsRequest(String headline, String body) {
    }

    public record Sent(int recipients) {
    }
}
