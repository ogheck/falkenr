package com.devtools.ui.relay;

import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

record RelayWebIdentity(String provider,
                        String subject,
                        String login,
                        String email,
                        String displayName) {

    static RelayWebIdentity fromOauthUser(OAuth2User user) {
        Map<String, Object> attrs = user.getAttributes();
        // GitHub: id, login, name, email (email may be null unless scope includes user:email)
        String id = stringAttr(attrs, "id");
        String login = stringAttr(attrs, "login");
        String name = stringAttr(attrs, "name");
        String email = stringAttr(attrs, "email");
        String displayName = (name != null && !name.isBlank())
                ? name
                : (login != null && !login.isBlank() ? login : "relay-user");
        String subject = (id != null && !id.isBlank()) ? id : (login == null ? "unknown" : login);
        return new RelayWebIdentity("github", subject, login, email, displayName);
    }

    private static String stringAttr(Map<String, Object> attrs, String key) {
        Object value = attrs == null ? null : attrs.get(key);
        return value == null ? null : String.valueOf(value);
    }
}

