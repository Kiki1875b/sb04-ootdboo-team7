package com.example.ootd.domain.user;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ootd.config.RedisTestContainerConfig;
import com.example.ootd.domain.user.dto.UserPagedResponse;
import com.example.ootd.domain.user.repository.UserRepository;
import com.example.ootd.security.Provider;
import com.example.ootd.security.jwt.JwtSession;
import com.example.ootd.security.jwt.JwtSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@Transactional
@SpringBootTest
@AutoConfigureMockMvc
public class UserServiceIntegrationTest extends RedisTestContainerConfig {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  UserRepository userRepository;

  @Autowired
  PasswordEncoder encoder;

  @Autowired
  JwtSessionRepository jwtSessionRepository;

  @Autowired
  EntityManager em;

  @MockitoBean
  private JavaMailSender sender;

  ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
  User user;
  User admin;

  @BeforeEach
  void setUp(){
    user = userRepository.save(
        new User("test", "test@gmail.com", encoder.encode("test"))
    );
    admin = new User("admin", "admin@email.com", encoder.encode("admin123"));

    admin.updateRole(UserRole.ROLE_ADMIN);

    userRepository.save(
        admin
    );


    User lockedOAuth2User = new User("oauth", "oauth@example.com", "provider-id", Provider.GOOGLE);
    lockedOAuth2User.updateLockStatus(true);
    userRepository.save(lockedOAuth2User);
  }

  @Test
  @DisplayName("사용자 목록 반환은 ADMIN계정 이어야 한다")
  void 사용자_목록_반환은_ADMIN계정_이어야_한다() throws Exception {
    //given
    MvcResult userResult = login("test@gmail.com", "test");
    String accessToken = extractAccessToken(userResult);

    mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isForbidden())
        .andReturn();

    MvcResult adminResult = login("admin@email.com", "admin123");
    String adminAccessToken = extractAccessToken(adminResult);

    MvcResult result = mockMvc.perform(get("/api/users")
            .header("Authorization", "Bearer " + adminAccessToken)
            .param("limit", "1")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING"))
        .andExpect(status().isOk())
        .andReturn();

    UserPagedResponse userPagedResponse = mapper.readValue(
        result.getResponse().getContentAsString(), UserPagedResponse.class);

    Assertions.assertThat(userPagedResponse).isNotNull();
  }

  @Test
  @DisplayName("ADMIN은 사용자 권한을 수정할 수 있다")
  void ADMIN은_사용자_권한을_수정할_수_있다() throws Exception {
    MvcResult adminResult = login("admin@email.com", "admin123");
    String adminAccessToken = extractAccessToken(adminResult);
    String json = """
        { "role" : "ADMIN" }
        """;

    mockMvc.perform(patch("/api/users/" + user.getId() +"/role")
            .contentType("application/json")
            .content(json)
            .header("Authorization", "Bearer " + adminAccessToken))
        .andExpect(status().isOk())
        .andReturn();

    User updatedUser = userRepository.findById(user.getId()).orElseThrow();

    Assertions.assertThat(updatedUser.getRole()).isEqualTo(UserRole.ROLE_ADMIN);

  }

  @Test
  @DisplayName("잠긴 계정은 로그인 할 수 없다")
  void 잠긴_계정은_로그인할_수_없다() throws Exception {
    // given
    User foundUser = userRepository.findById(user.getId()).orElseThrow();
    foundUser.updateLockStatus(true);
    em.flush();
    String json = """
        {
          "email": "test@gmail.com",
          "password": "test"
        }
        """;

    mockMvc.perform(post("/api/auth/sign-in")
        .contentType("application/json")
        .content(json))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("계정을 잠글시 강제 로그아웃")
  void 계정을_잠글시_강제_로그아웃() throws Exception {
    // given
    MvcResult userResult = login("test@gmail.com", "test");
    Optional<JwtSession> sessionOptional = jwtSessionRepository.findByUser_Id(user.getId());
    Assertions.assertThat(sessionOptional).isPresent();
    String json = """
        { "locked" : "true" }
        """;

    em.flush();
    em.clear();
    // when
    MvcResult adminResult = login("admin@email.com", "admin123");
    String adminAccessToken = extractAccessToken(adminResult);

    mockMvc.perform(patch("/api/users/" + user.getId() +"/lock")
            .contentType("application/json")
            .content(json)
            .header("Authorization", "Bearer " + adminAccessToken))
        .andExpect(status().isOk())
        .andReturn();

    // then
    Optional<JwtSession> sessionOptional2 = jwtSessionRepository.findByUser_Id(user.getId());
    Assertions.assertThat(sessionOptional2).isEmpty();

  }

  private String extractAccessToken(MvcResult loginResult) throws Exception {

    String accessToken = loginResult.getResponse().getContentAsString();
    if (accessToken.startsWith("\"") && accessToken.endsWith("\"")) {
      accessToken = accessToken.substring(1, accessToken.length() - 1);
    }


    return accessToken;
  }

  private MvcResult login(String email, String password) throws Exception {

    String json = """
        {
          "email": "%s",
          "password": "%s"
        }
        """.formatted(email, password);

    return mockMvc.perform(post("/api/auth/sign-in")
            .contentType("application/json")
            .content(json))
        .andExpect(status().isOk())
        .andReturn();
  }
}
