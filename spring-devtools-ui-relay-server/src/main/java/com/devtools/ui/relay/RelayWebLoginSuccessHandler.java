package com.devtools.ui.relay;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
class RelayWebLoginSuccessHandler implements AuthenticationSuccessHandler {

    static final String ACCOUNT_SESSION_ATTR = "relay.accountSession";

    private final InMemoryRelaySessionStore sessionStore;

    RelayWebLoginSuccessHandler(InMemoryRelaySessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OAuth2User oauthUser)) {
            response.sendRedirect("/app");
            return;
        }

        RelayWebIdentity identity = RelayWebIdentity.fromOauthUser(oauthUser);
        String accountSessionToken = sessionStore.bootstrapWebIdentity(identity);
        HttpSession session = request.getSession(true);
        session.setAttribute(ACCOUNT_SESSION_ATTR, accountSessionToken);
        response.sendRedirect("/app");
    }
}

