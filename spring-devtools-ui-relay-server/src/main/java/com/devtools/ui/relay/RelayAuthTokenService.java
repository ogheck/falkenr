package com.devtools.ui.relay;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

final class RelayAuthTokenService {

    private final RelayServerProperties properties;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    RelayAuthTokenService(RelayServerProperties properties) {
        this.properties = properties;
        this.algorithm = Algorithm.HMAC256(properties.authSecret());
        this.verifier = JWT.require(algorithm)
                .withIssuer(properties.authIssuer())
                .build();
    }

    RelayAccountSessionToken issueAccountSession(String accountId, String organizationId, String role, Instant expiresAt) {
        String jti = "acct_session_" + UUID.randomUUID();
        String token = JWT.create()
                .withIssuer(properties.authIssuer())
                .withJWTId(jti)
                .withSubject(accountId)
                .withClaim("org", organizationId)
                .withClaim("role", role)
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm);
        return new RelayAccountSessionToken(token, jti);
    }

    RelayAccountSessionClaims verifyAccountSession(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            String accountId = jwt.getSubject();
            String organizationId = jwt.getClaim("org").asString();
            String role = jwt.getClaim("role").asString();
            String jti = jwt.getId();
            Date expiresAt = jwt.getExpiresAt();
            if (accountId == null || accountId.isBlank() || organizationId == null || organizationId.isBlank() || jti == null || jti.isBlank()) {
                throw new RelayAuthException("Invalid relay account session");
            }
            return new RelayAccountSessionClaims(accountId, organizationId, role, jti, expiresAt == null ? null : expiresAt.toInstant());
        } catch (JWTVerificationException exception) {
            throw new RelayAuthException("Invalid relay account session");
        }
    }

    RelayViewerSessionToken issueViewerSession(String viewerSessionId,
                                              String sessionId,
                                              String organizationId,
                                              String accountId,
                                              String role,
                                              String viewerName,
                                              Instant expiresAt) {
        String jti = viewerSessionId;
        String token = JWT.create()
                .withIssuer(properties.authIssuer())
                .withJWTId(jti)
                .withSubject(accountId)
                .withClaim("org", organizationId)
                .withClaim("sid", sessionId)
                .withClaim("role", role)
                .withClaim("viewerName", viewerName)
                .withClaim("type", "viewer-session")
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm);
        return new RelayViewerSessionToken(token, jti);
    }

    RelayViewerSessionClaims verifyViewerSession(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            if (!"viewer-session".equals(jwt.getClaim("type").asString())) {
                throw new RelayAuthException("Invalid relay viewer session");
            }
            String accountId = jwt.getSubject();
            String organizationId = jwt.getClaim("org").asString();
            String sessionId = jwt.getClaim("sid").asString();
            String role = jwt.getClaim("role").asString();
            String viewerName = jwt.getClaim("viewerName").asString();
            String jti = jwt.getId();
            Date expiresAt = jwt.getExpiresAt();
            if (accountId == null || accountId.isBlank()
                    || organizationId == null || organizationId.isBlank()
                    || sessionId == null || sessionId.isBlank()
                    || jti == null || jti.isBlank()) {
                throw new RelayAuthException("Invalid relay viewer session");
            }
            return new RelayViewerSessionClaims(accountId, organizationId, sessionId, role, viewerName, jti, expiresAt == null ? null : expiresAt.toInstant());
        } catch (JWTVerificationException exception) {
            throw new RelayAuthException("Invalid relay viewer session");
        }
    }

    record RelayAccountSessionToken(String token, String jti) {
    }

    record RelayAccountSessionClaims(String accountId, String organizationId, String role, String jti, Instant expiresAt) {
    }

    record RelayViewerSessionToken(String token, String jti) {
    }

    record RelayViewerSessionClaims(String accountId,
                                    String organizationId,
                                    String sessionId,
                                    String role,
                                    String viewerName,
                                    String jti,
                                    Instant expiresAt) {
    }
}
