package com.example.ootd.domain.user.util;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailService {

  private final JavaMailSender mailSender;
  @Retryable(
      retryFor = { MailException.class },
      maxAttempts = 3,
      backoff = @Backoff(delay = 2000, multiplier = 2)
  )
  public void sendEmail(String email, String tempPassword){
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(email);
    message.setSubject("[OOTD] 임시 비밀번호 안내");
    message.setText("임시 비밀번호는 다음과 같습니다:\n\n" + tempPassword + "\n\n로그인 후 비밀번호를 꼭 변경해주세요.");

    mailSender.send(message);
  }

  @Recover
  public void recover(MailException e, String email){
    // TODO : 복구 로직
    // TODO : 비동기 작업 오류 레포지토리 의논

  }
}
