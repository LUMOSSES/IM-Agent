package com.aiagent.chatagent.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * JWT 工具类 — 与 ChatAgent 完全一致: HS512, 密钥 16×"goat"
 */
public class JwtUtil {

    // 来自 ChatAgent ConfigEnum.TOKEN_SECRET_KEY 真实值（64字节 = 512 bits）
    private static final String SECRET =
            "goatgoatgoatgoatgoatgoatgoatgoatgoatgoatgoatgoatgoatgoatgoatgoat";

    public static Long parseUserId(String token) {
        byte[] keyBytes = SECRET.getBytes(StandardCharsets.UTF_8);
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(keyBytes)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return Long.parseLong(claims.getSubject());
    }
}
