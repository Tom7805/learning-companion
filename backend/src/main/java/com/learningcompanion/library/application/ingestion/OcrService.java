package com.learningcompanion.library.application.ingestion;

import com.learningcompanion.ai.config.application.AiConfigResolver;
import com.learningcompanion.ai.config.domain.AiFeature;
import com.learningcompanion.ai.gateway.AiGateway;
import com.learningcompanion.ai.gateway.model.VisionRequest;
import com.learningcompanion.shared.exception.BusinessException;
import com.learningcompanion.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Nhận dạng chữ cho trang quét và ảnh qua lớp kết nối (QTN-04), theo cấu hình đang phát hành của
 * {@link AiFeature#DOCUMENT_OCR}. Ngoài số lần thử của lớp kết nối, một trang lỗi được thử lại riêng thêm một lần; vẫn
 * lỗi thì báo đúng trang đó (NCL-05-CN-003 TC-03). Việc xử lý tài liệu không trừ lượt của người học.
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    /** Số lần thử lại riêng một trang sau khi lớp kết nối đã hết số lần thử của nó. */
    static final int PAGE_RETRIES = 1;

    private final AiGateway gateway;
    private final AiConfigResolver configs;

    public OcrService(AiGateway gateway, AiConfigResolver configs) {
        this.gateway = gateway;
        this.configs = configs;
    }

    public String recognize(int page, byte[] image, String mimeType) {
        String instructions = configs.resolve(AiFeature.DOCUMENT_OCR).systemPrompt();
        VisionRequest request = new VisionRequest(image, mimeType, instructions);
        for (int attempt = 0; ; attempt++) {
            try {
                return gateway.recognizeText(AiFeature.DOCUMENT_OCR.name(), request,
                        configs.tuning(AiFeature.DOCUMENT_OCR)).text();
            } catch (BusinessException ex) {
                if (ex.code() != ErrorCode.AI_TEMPORARILY_UNAVAILABLE) {
                    throw ex;
                }
                if (attempt >= PAGE_RETRIES) {
                    throw new PageRecognitionException(page, ex);
                }
                log.info("Nhận dạng chữ lỗi ở trang {}, thử lại riêng trang này", page);
            }
        }
    }

    /** Nhận dạng chữ vẫn lỗi ở một trang sau khi đã thử lại. */
    public static class PageRecognitionException extends RuntimeException {

        private final int page;

        public PageRecognitionException(int page, Throwable cause) {
            super("Không nhận dạng được chữ ở trang " + page, cause);
            this.page = page;
        }

        public int page() {
            return page;
        }
    }
}
