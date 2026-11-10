package com.learningcompanion.shared.web;

public final class ApiPaths {

    public static final String V1 = "/api/v1";
    public static final String AUTH = V1 + "/auth";
    public static final String LEGAL = V1 + "/legal";
    public static final String DEV = V1 + "/dev";
    public static final String LIBRARY = V1 + "/library";
    public static final String WORKSPACES = V1 + "/workspaces";
    public static final String FILES = V1 + "/files";
    public static final String ADMIN = V1 + "/admin";
    public static final String PROFILES = V1 + "/profiles";
    public static final String ACCOUNTS = V1 + "/accounts";
    public static final String ANALYTICS = V1 + "/analytics";
    public static final String EXPORTS = V1 + "/exports";
    public static final String UI_PREFERENCES = V1 + "/ui-preferences";
    public static final String NOTIFICATIONS = V1 + "/notifications";
    public static final String QUOTA = V1 + "/quota";

    /**
     * Chức năng học tập, chỉ vai trò người học dùng được; quản trị viên chỉ thấy siêu dữ liệu vận hành (QTN-07).
     * Story thêm chức năng học tập mới thì thêm đường dẫn vào đây.
     */
    public static final String[] LEARNING_FEATURES = {LIBRARY + "/**", WORKSPACES, WORKSPACES + "/**", EXPORTS,
            EXPORTS + "/**"};

    private ApiPaths() {
    }
}
