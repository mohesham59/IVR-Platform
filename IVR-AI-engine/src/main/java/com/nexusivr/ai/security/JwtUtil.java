package com.nexusivr.ai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    // Minimum 256-bit secret key for HMAC-SHA256
    private static final String SECRET_KEY_STR = "NexusIVR_Super_Secret_Jwt_Signing_Key_2026_Enterprise_Edition_256_Bit!";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET_KEY_STR.getBytes(StandardCharsets.UTF_8));
    
    // 24 hours expiration
    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000L;

    public static String generateToken(String userId, String email, String username, boolean isSuperadmin, String activeTenantId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION_MS);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("username", username);
        claims.put("isSuperadmin", isSuperadmin);
        claims.put("activeTenantId", activeTenantId);

        return Jwts.builder()
                .claims(claims)
                .subject(userId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(SECRET_KEY)
                .compact();
    }

    public static Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            logger.warn("Invalid or expired JWT token: {}", e.getMessage());
            return null;
        }
    }
}
