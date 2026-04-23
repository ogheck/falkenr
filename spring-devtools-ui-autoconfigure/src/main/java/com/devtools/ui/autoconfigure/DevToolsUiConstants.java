package com.devtools.ui.autoconfigure;

final class DevToolsUiConstants {

    static final String ROOT_PATH = "/_dev";
    static final String API_BASE_PATH = ROOT_PATH + "/api";
    static final String STATIC_RESOURCE_LOCATION = "classpath:/META-INF/spring-devtools-ui/";
    static final String INDEX_PATH = ROOT_PATH + "/index.html";
    static final String ASSETS_PATH = ROOT_PATH + "/assets/**";
    static final int DEFAULT_REQUEST_LIMIT = 100;
    static final int DEFAULT_LOG_LIMIT = 500;
    static final int DEFAULT_DB_QUERY_LIMIT = 100;
    static final int DEFAULT_BODY_CACHE_LIMIT = 32 * 1024;
    static final int DEFAULT_BODY_PREVIEW_LIMIT = 8 * 1024;

    private DevToolsUiConstants() {
    }
}
