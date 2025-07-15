package com.example.ootd.security.jwt;

import com.example.ootd.domain.user.User;
import com.example.ootd.exception.ErrorCode;
import com.example.ootd.exception.OotdException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

  @Value("${app.jwt.secret}")
  private String jwtSecret;

  @Value("${app.jwt.access-token-expiration}")
  private long accessTokenExpiration;

  @Value("${app.jwt.refresh-token-expiration}")
  private long refreshTokenExpiration;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JwtSessionRepository jwtSessionRepository;
  private final BlackList blackList;

  // JwtSession 객체 생성 및 저장 (이미 존재한다면 삭제 + 블렉리스트 등록)
  @Transactional
  public JwtSession generateJwtSession(User user) {
    Optional<JwtSession> sessionOptional = jwtSessionRepository.findByUser_Id(user.getId());

    if(sessionOptional.isPresent()){
      JwtSession session = sessionOptional.get();

      Instant expirationTime = extractExpiry(session.getAccessToken());
      blackList.addToBlacklist(session.getAccessToken(), expirationTime);

      jwtSessionRepository.delete(session);
    }
//        .ifPresent(session -> {
//          Instant expirationTime = extractExpiry(session.getAccessToken());
//          BlackList.addToBlacklist(session.getAccessToken(), expirationTime);
//
//          jwtSessionRepository.delete(session);
//        });

    jwtSessionRepository.flush(); // TODO : 이유 분석.. 왜 delete 가 먼저 적용 안되는지

    String accessToken = generateAccessToken(user);

    String refreshToken = generateRefreshToken(user.getId());

    JwtSession session = new JwtSession(user, accessToken, refreshToken);

    return jwtSessionRepository.save(session);
  }

  @Transactional
  public JwtSession rotateRefreshToken(String token){

    JwtSession session = jwtSessionRepository.findByRefreshToken(token)
        .orElseThrow(() -> new OotdException(ErrorCode.AUTHENTICATION_FAILED));

    if(!validateToken(token)){
      jwtSessionRepository.delete(session);
      throw new OotdException(ErrorCode.AUTHENTICATION_FAILED);
    }

    blackList.addToBlacklist(session.getAccessToken(), extractExpiry(session.getAccessToken()));
    blackList.addToBlacklist(session.getRefreshToken(), extractExpiry(session.getRefreshToken()));

    User user = session.getUser();
    String newAccessToken = generateAccessToken(user);
    String newRefreshToken = generateRefreshToken(user.getId());

    session.updateTokens(newAccessToken, newRefreshToken);

    return jwtSessionRepository.save(session);
  }

  // 토큰 유효성 검증
  public boolean validateToken(String token) {
    try {

      if (blackList.isBlacklisted(token)) {
        log.warn("블랙리스트에 등록된 토큰입니다. TOKEN : {}", token);
        return false;
      }

      Jwts.parserBuilder()
          .setSigningKey(getSigningKey())
          .build()
          .parseClaimsJws(token);

      return true;
    } catch (ExpiredJwtException e) {
      log.warn("토큰이 만료되었습니다: {}", e.getMessage());
      return false;
    } catch (JwtException e) {
      log.warn("유효하지 않은 토큰입니다: {}", e.getMessage());
      return false;
    }
  }

  @Transactional
  public void invalidateToken(String token) {
    JwtSession session = jwtSessionRepository.findByRefreshToken(token)
        .orElseThrow(() -> new OotdException(ErrorCode.AUTHENTICATION_FAILED));

    blackList.addToBlacklist(session.getAccessToken(), extractExpiry(session.getAccessToken()));
    blackList.addToBlacklist(session.getRefreshToken(), extractExpiry(session.getRefreshToken()));

    jwtSessionRepository.delete(session);
  }
  // 만료 시간 추출
  public Instant extractExpiry(String token){
    try{
      Claims claims = Jwts.parserBuilder()
          .setSigningKey(getSigningKey())
          .build()
          .parseClaimsJws(token)
          .getBody();

      Date expiration = claims.getExpiration();
      return expiration.toInstant();
    } catch(ExpiredJwtException e){
      Date expiration = e.getClaims().getExpiration();
      return expiration.toInstant();
    } catch (Exception e){
      log.warn("[JWT Parse ERROR] : {}" , e.getMessage());
      throw new OotdException(ErrorCode.AUTHENTICATION_FAILED);
    }
  }

  // 이메일 추출
  public String extractEmail(String token){
    try{
      Claims claims = Jwts.parserBuilder()
          .setSigningKey(getSigningKey())
          .build()
          .parseClaimsJws(token)
          .getBody();


      return objectMapper.convertValue(claims.get("email"), String.class);
    }catch (Exception e){
      throw new OotdException(ErrorCode.AUTHENTICATION_FAILED);
    }
  }

  private String generateAccessToken(User user){

    Instant now = Instant.now();
    Instant expirationTime = now.plusMillis(accessTokenExpiration);

    try{
      return Jwts.builder()
          .setSubject(user.getEmail())
          .claim("userId", user.getId().toString())
          .claim("role", user.getRole().name().replace("ROLE_", ""))
          .claim("name", user.getName())
          .claim("email", user.getEmail())
          .setIssuedAt(Date.from(now))
          .setExpiration(Date.from(expirationTime))
          .signWith(getSigningKey())
          .compact();
    } catch (Exception e){
      throw new OotdException(ErrorCode.AUTHENTICATION_FAILED);
    }
  }

  private String generateRefreshToken(UUID userId){
    Instant now = Instant.now();
    Instant expirationTime = now.plusMillis(refreshTokenExpiration);

    return Jwts.builder()
        .claim("userId", userId.toString())
        .claim("type", "REFRESH")
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(expirationTime))
        .signWith(getSigningKey())
        .compact();
  }

  private Key getSigningKey() {
    return Keys.hmacShaKeyFor(jwtSecret.getBytes());
  }

}
