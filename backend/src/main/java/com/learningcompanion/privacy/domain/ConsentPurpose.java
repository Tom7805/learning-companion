package com.learningcompanion.privacy.domain;

/** Mục đích xử lý dữ liệu, mỗi mục đích có đồng ý riêng (QTN-03). */
public enum ConsentPurpose {
    TERMS_OF_SERVICE(true),
    PRIVACY_POLICY(true),
    ANONYMOUS_USAGE_ANALYTICS(false),
    PRODUCT_NEWS_EMAIL(false);

    private final boolean required;

    ConsentPurpose(boolean required) {
        this.required = required;
    }

    public boolean isRequired() {
        return required;
    }
}
