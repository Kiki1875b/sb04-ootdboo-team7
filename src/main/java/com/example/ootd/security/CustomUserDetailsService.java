package com.example.ootd.security;

import com.example.ootd.domain.user.User;
import com.example.ootd.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {


  private final UserRepository userRepository;

  @Override
//  @Cacheable(value = "principalUser", key = "#email")
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user = userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException()); // TODO : 예외 상세 처리

    return new CustomUserDetails(user);
  }
}
