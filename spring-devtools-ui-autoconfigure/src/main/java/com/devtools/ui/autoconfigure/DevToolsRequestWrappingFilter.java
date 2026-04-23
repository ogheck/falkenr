package com.devtools.ui.autoconfigure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

class DevToolsRequestWrappingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(DevToolsUiConstants.ROOT_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = request instanceof ContentCachingRequestWrapper contentCachingRequestWrapper
                ? contentCachingRequestWrapper
                : new ContentCachingRequestWrapper(request, DevToolsUiConstants.DEFAULT_BODY_CACHE_LIMIT);

        filterChain.doFilter(wrappedRequest, response);
    }
}
