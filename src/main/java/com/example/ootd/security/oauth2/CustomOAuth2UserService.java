package com.example.ootd.security.oauth2;

import com.example.ootd.domain.user.User;
import com.example.ootd.domain.user.repository.UserRepository;
import com.example.ootd.exception.ErrorCode;
import com.example.ootd.exception.OotdException;
import com.example.ootd.security.Provider;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private final UserRepository userRepository;

  @Override
  @Transactional
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oAuth2User = super.loadUser(userRequest);

    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    Map<String, Object> attributes = oAuth2User.getAttributes();

    OAuth2Provider provider = OAuth2Provider.valueOf(registrationId.toUpperCase());
    User user = provider.getOrCreateUser(attributes, userRepository);

    if(Boolean.TRUE.equals(user.getIsLocked())){
      throw new OotdException(ErrorCode.AUTHENTICATION_FAILED);
    }

    // OAuth2User 의 attributes 는 unmodifiable
    Map<String, Object> attributesCopy = new HashMap<>(attributes);
    attributesCopy.put("email", user.getEmail());

    return new CustomOAuth2User(user, attributesCopy);
  }



  enum OAuth2Provider {
    GOOGLE {
      @Override
      User getOrCreateUser(Map<String, Object> attributes, UserRepository userRepository){
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String providerId = (String) attributes.get("sub");

        return userRepository.findByEmail(email).orElseGet(() -> {
          User newUser = new User(name, email, providerId, Provider.GOOGLE);
          return userRepository.save(newUser);
        });
      }
    },
    KAKAO{
      @Override
      User getOrCreateUser(Map<String, Object> attributes, UserRepository userRepository){

        String providerId = attributes.get("id").toString();
        String nickname = (String) ((Map<?, ?>)  attributes.get("properties")).get("nickname");
        String email = nickname + "@kakao.com";

        return userRepository.findByEmail(email).orElseGet(() -> {
          User newUser = new User(nickname, email, providerId, Provider.KAKAO);
          return userRepository.save(newUser);
        });
      }
    };

    abstract User getOrCreateUser(Map<String, Object> attributes, UserRepository userRepository);
  }

}


