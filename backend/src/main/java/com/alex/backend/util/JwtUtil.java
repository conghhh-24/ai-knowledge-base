package com.alex.backend.util;


import com.alex.backend.vo.UserVO;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;

    //生成完整 JWT 字符串
   public String generateToken(UserVO user) {
       JwtBuilder builder = Jwts.builder()
               .claim("userId", user.getId())
               .claim("username", user.getUsername())
               .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
               .expiration(new Date(System.currentTimeMillis() + 60 * 60 * 1000));
       return builder.compact();
   }
    //验证 token并读取数据
    public Claims parseToken(String token){
       JwtParser parser = Jwts.parser()
               .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
               .build();
        Jws<Claims> result = parser.parseSignedClaims(token);

       return result.getPayload();
    }

}
