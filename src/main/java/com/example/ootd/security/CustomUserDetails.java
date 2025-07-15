package com.example.ootd.security;

import com.example.ootd.domain.user.User;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;


@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails, PrincipalUser {

  private final User user;

  @Override
  public User getUser() {
    return user;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority(user.getRole().name()));
  }

  @Override
  public boolean isOAuthUser() {
    return false;
  }

  @Override
  public String getPassword() {
    return user.getPassword();
  }

  @Override
  public String getUsername() {
    return user.getEmail();
  }

  @Override
  public boolean isCredentialsNonExpired() {

    if(user.isTempPassword() && user.getTempPasswordExpiration() != null){
      return user.getTempPasswordExpiration().isAfter(LocalDateTime.now());
    }

    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return !user.getIsLocked();
  }
}
