package com.example.ootd.security.jwt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

import com.example.ootd.TestEntityFactory;
import com.example.ootd.domain.user.User;
import com.example.ootd.exception.OotdException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class JwtServiceTest {

  @InjectMocks
  private JwtService jwtService;

  @Mock
  private JwtSessionRepository jwtSessionRepository;

  @Mock
  private BlackList blackList;

  private final String TEST_SECRET = "thisismytestsecretkeyanditshouldbelongenoughforjwtsigning";
  private final long TEST_ACCESS_TOKEN_EXPIRATION = 3600000;
  private final long TEST_REFRESH_TOKEN_EXPIRATION = 604800000;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private User user;

  @BeforeEach
  void setUp(){
    user = TestEntityFactory.createUser();
    ReflectionTestUtils.setField(jwtService, "jwtSecret", TEST_SECRET);
    ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", TEST_ACCESS_TOKEN_EXPIRATION);
    ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", TEST_REFRESH_TOKEN_EXPIRATION);
  }

  @Test
  @DisplayName("정상적으로 세션을 생성할 수 있다")
  void 정상적으로_세션을_생성할수_있다(){
    // given
    given(jwtSessionRepository.findByUser_Id(any()))
        .willReturn(Optional.empty());
    given(jwtSessionRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

    // when
    JwtSession session = jwtService.generateJwtSession(user);

    // then
    assertThat(session.getAccessToken()).isNotNull();
    assertThat(session.getRefreshToken()).isNotNull();
    verify(jwtSessionRepository).save(any(JwtSession.class));
  }

  @Test
  @DisplayName("세션이 존재하는 사용자 검증")
  void 세션이_존재하면_블렉리스트_사용() throws InterruptedException {
    // given
    given(jwtSessionRepository.findByUser_Id(any()))
        .willReturn(Optional.empty());
    given(jwtSessionRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    JwtSession session = jwtService.generateJwtSession(user);

    given(jwtSessionRepository.findByRefreshToken(session.getRefreshToken()))
        .willReturn(Optional.of(session));
    String oldAccessToken = session.getAccessToken();
    String oldRefreshToken = session.getRefreshToken();
    Thread.sleep(1000);


    // when
    JwtSession newSession = jwtService.rotateRefreshToken(oldRefreshToken);

    // then
    assertThat(newSession.getAccessToken()).isNotEqualTo(oldAccessToken);
    assertThat(newSession.getRefreshToken()).isNotEqualTo(oldRefreshToken);
    assertThat(newSession.getUser()).isEqualTo(session.getUser());

    verify(jwtSessionRepository, times(0)).delete(any());
    verify(blackList).addToBlacklist(eq(oldAccessToken), any());
    verify(blackList).addToBlacklist(eq(oldRefreshToken), any());
  }

  @Test
  @DisplayName("세션 없이 rotate 시 예외")
  void 세션_없이_rotate_예외(){
    // given
    String token = "";
    given(jwtSessionRepository.findByRefreshToken(any())).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> jwtService.rotateRefreshToken(token))
        .isInstanceOf(OotdException.class);
  }

  @Test
  @DisplayName("이메일을 파싱할 수 있다")
  void 토큰에서_이메일_파싱_성공(){
    // given
    given(jwtSessionRepository.findByUser_Id(any()))
        .willReturn(Optional.empty());
    given(jwtSessionRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

    // when
    JwtSession session = jwtService.generateJwtSession(user);
    String email = jwtService.extractEmail(session.getAccessToken());

    // then
    assertThat(email).isEqualTo("test@gmail.com");
  }

  @Test
  @DisplayName("만료기간이 지난 토큰은 유효성 검증 실패")
  void 만료기간이_지난_토큰은_유효성_검증_실패() throws InterruptedException{
    // given
    ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 1L);
    given(jwtSessionRepository.findByUser_Id(any())).willReturn(Optional.empty());
    given(jwtSessionRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));


    // when
    JwtSession session = jwtService.generateJwtSession(user);
    Thread.sleep(10);

    // then
    boolean result = jwtService.validateToken(session.getAccessToken());
    assertThat(result).isFalse();
  }

}
