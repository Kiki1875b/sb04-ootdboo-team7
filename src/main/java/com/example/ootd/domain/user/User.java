package com.example.ootd.domain.user;

import com.example.ootd.domain.image.entity.Image;
import com.example.ootd.domain.location.Location;
import com.example.ootd.domain.user.dto.ProfileUpdateRequest;
import com.example.ootd.security.Provider;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(orphanRemoval = true)
  @JoinColumn(name = "image_id", nullable = true)
  private Image image;

  @ManyToOne(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
  @JoinColumn(name = "location_id")
  private Location location;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Column
  private String password;

  @Column
  @Enumerated(EnumType.STRING)
  private UserRole role;

  @Column
  private Boolean isLocked;

  @Column
  @Enumerated(EnumType.STRING)
  private Provider provider;

  @Column
  private String providerId;

  @Column
  @CreatedDate
  private LocalDateTime createdAt;

  @Column
  @LastModifiedDate
  private LocalDateTime updatedAt;

  @Column
  @Enumerated(EnumType.STRING)
  private Gender gender;

  @Column
  private LocalDate birthDate;

  @Column
  private int temperatureSensitivity;

  @Column
  private boolean isTempPassword;

  @Column
  private LocalDateTime tempPasswordExpiration;

  @Builder
  public User(String name, String email, String password, UserRole role, Boolean isLocked,
      Provider provider, String providerId, Gender gender, LocalDate birthDate,
      int temperatureSensitivity, boolean isTempPassword, LocalDateTime tempPasswordExpiration) {
    this.name = name;
    this.email = email;
    this.password = password;
    this.role = role != null ? role : UserRole.ROLE_USER;
    this.isLocked = isLocked != null ? isLocked : false;
    this.provider = provider;
    this.providerId = providerId;
    this.gender = gender != null ? gender : Gender.OTHER;
    this.birthDate = birthDate != null ? birthDate : LocalDate.now();
    this.temperatureSensitivity = temperatureSensitivity != 0 ? temperatureSensitivity : 3;
    this.isTempPassword = isTempPassword;
    this.tempPasswordExpiration = tempPasswordExpiration;
  }

  public User(String name, String email, String password) {
    this.name = name;
    this.email = email;
    this.password = password;
    this.role = UserRole.ROLE_USER;
    this.isLocked = false;
    this.provider = null;
    this.providerId = null;
    this.gender = Gender.OTHER;
    this.birthDate = LocalDate.now(); // TODO: 변경
    this.temperatureSensitivity = 3;
    this.isTempPassword = false;
    this.tempPasswordExpiration = null;
  }

  public User(String name, String email, String providerId, Provider provider) {
    this.name = name;
    this.email = email;
    this.password = null;
    this.role = UserRole.ROLE_USER;
    this.isLocked = false;
    this.provider = provider;
    this.providerId = providerId;
    this.gender = Gender.OTHER;
    this.birthDate = LocalDate.now();
    this.temperatureSensitivity = 3;
    this.isTempPassword = false;
    this.tempPasswordExpiration = null;
  }

  public boolean isTempPassword() {
    return this.isTempPassword;
  }

  public void resetPassword(String tempPassword) {
    this.isTempPassword = true;
    this.tempPasswordExpiration = LocalDateTime.now().plusMinutes(10);
    this.password = tempPassword;
  }

  public void updateRole(UserRole role) {
    this.role = role;
  }

  public void updateProfile(ProfileUpdateRequest request, Image image) {
    this.image = image == null ? this.image : image;
    this.name = request.name() == null ? this.name : request.name();
    this.gender = request.gender() == null ? this.gender : request.gender();
    this.birthDate = request.birthDate() == null ? this.birthDate : request.birthDate();
//    this.location = request.location() == null ? this.location : request.location();
    this.temperatureSensitivity =
        request.temperatureSensitivity() == 0 ? this.temperatureSensitivity
            : request.temperatureSensitivity();
  }

  public void updateLocation(Location location) {
    this.location = location;
  }

  public void updatePassword(String password) {
    this.password = password;
    this.isTempPassword = false;
    this.tempPasswordExpiration = null;
  }

  public void updateLockStatus(boolean isLocked) {
    this.isLocked = isLocked;
  }

  // 테스트 코드 작성할 때 location이 없으면 제약 조건 위반 에러가 발생해 잠시 만들어뒀습니다 !
  public User(String name, String email, String password, Location location) {
    this.name = name;
    this.email = email;
    this.password = password;
    this.location = location;
    this.role = UserRole.ROLE_USER;
    this.isLocked = false;
    this.provider = null;
    this.providerId = null;
    this.gender = Gender.OTHER;
    this.birthDate = LocalDate.now(); // TODO: 변경
    this.temperatureSensitivity = 3;

  }

}
