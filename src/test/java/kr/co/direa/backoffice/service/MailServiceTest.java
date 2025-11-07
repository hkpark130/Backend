package kr.co.direa.backoffice.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.mail.javamail.JavaMailSender;
import kr.co.direa.backoffice.config.ApprovalProperties;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private CommonLookupService commonLookupService;

    @Mock
    private ApprovalProperties approvalProperties;

    private MailService mailService;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        mailService = new MailService(mailSender, templateEngine, commonLookupService, approvalProperties);
        ReflectionTestUtils.setField(mailService, "mailUsername", "noreply@example.com");
        ReflectionTestUtils.setField(mailService, "frontendBaseUrl", "http://localhost:5173");
        when(approvalProperties.getDefaultApprovers()).thenReturn(Collections.emptyList());

        mimeMessage = new MimeMessage((Session) null);
        lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        lenient().when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html></html>");
    }

    @Test
    void sendPasswordResetMail_sendsEmailWhenKeycloakReturnsAddress() {
        CommonLookupService.KeycloakUserInfo userInfo = new CommonLookupService.KeycloakUserInfo(
            UUID.randomUUID(), "tester", "tester@direa.co.kr", "테스터");
        when(commonLookupService.resolveKeycloakUserInfoByUsername("tester"))
            .thenReturn(Optional.of(userInfo));

        mailService.sendPasswordResetMail("tester", "TEMP-1234");

        verify(commonLookupService).resolveKeycloakUserInfoByUsername("tester");
        verify(templateEngine).process(eq("password-reset"), any(Context.class));
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendPasswordResetMail_skipsWhenEmailMissing() {
        CommonLookupService.KeycloakUserInfo userInfo = new CommonLookupService.KeycloakUserInfo(
            UUID.randomUUID(), "tester", null, "테스터");
        when(commonLookupService.resolveKeycloakUserInfoByUsername("tester"))
            .thenReturn(Optional.of(userInfo));

        mailService.sendPasswordResetMail("tester", "TEMP-1234");

        verify(commonLookupService).resolveKeycloakUserInfoByUsername("tester");
        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}