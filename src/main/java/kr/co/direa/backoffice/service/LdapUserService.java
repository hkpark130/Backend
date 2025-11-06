package kr.co.direa.backoffice.service;

import javax.naming.Name;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import kr.co.direa.backoffice.domain.ldap.LdapUser;
import kr.co.direa.backoffice.dto.ldap.LdapUserCreateRequest;
import kr.co.direa.backoffice.dto.ldap.LdapUserResponse;
import kr.co.direa.backoffice.exception.CustomException;
import kr.co.direa.backoffice.exception.code.CustomErrorCode;
import kr.co.direa.backoffice.repository.LdapUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.ldap.support.LdapUtils;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LdapUserService {

    private static final List<String> DEFAULT_OBJECT_CLASSES = List.of(
            "top",
            "person",
            "organizationalPerson",
            "inetOrgPerson",
            "posixAccount"
    );

    private final SecureRandom secureRandom = new SecureRandom();
    private final LdapTemplate ldapTemplate;
    private final LdapUserRepository ldapUserRepository;
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Value("${spring.ldap.base:}")
    private String baseDn;

    @Value("${app.ldap.user-base:cn=Users}")
    private String userBase;

    @Value("${app.ldap.login-shell:/bin/bash}")
    private String loginShell;

    @Value("${app.ldap.password-length:12}")
    private Integer passwordLength;

    public List<LdapUserResponse> fetchUsers() {
        return ldapUserRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public boolean isCnAvailable(String cn) {
        if (!StringUtils.hasText(cn)) {
            return false;
        }
        return ldapUserRepository.findByCn(cn.trim()).isEmpty();
    }

    public boolean isUidNumberAvailable(Integer uidNumber) {
        if (uidNumber == null) {
            return false;
        }
    return ldapUserRepository.findByUidNumber(uidNumber).isEmpty();
    }

    public void createUser(LdapUserCreateRequest request) {
        if (!isCnAvailable(request.getCn())) {
            throw new CustomException(CustomErrorCode.LDAP_CN_CONFLICT);
        }
        if (!isUidNumberAvailable(request.getUidNumber())) {
            throw new CustomException(CustomErrorCode.LDAP_UID_CONFLICT);
        }

        String passwordSource = StringUtils.hasText(request.getUserPassword())
                ? request.getUserPassword()
                : String.valueOf(request.getUidNumber());

        Name dn = buildUserDn(request.getCn());
        Attributes attrs = new BasicAttributes(true);
        BasicAttribute objectClass = new BasicAttribute("objectClass");
        DEFAULT_OBJECT_CLASSES.forEach(objectClass::add);
        attrs.put(objectClass);

        putAttribute(attrs, "cn", request.getCn());
        putAttribute(attrs, "sn", resolveSn(request));
        putAttribute(attrs, "uid", request.getUid());
        putAttribute(attrs, "uidNumber", String.valueOf(request.getUidNumber()));
        putAttribute(attrs, "gidNumber", String.valueOf(request.getGidNumber()));
        putAttribute(attrs, "homeDirectory", request.getHomeDirectory());
        putAttribute(attrs, "loginShell", loginShell);
        putAttribute(attrs, "mail", request.getMail());
        putAttribute(attrs, "ou", request.getOu());
        if (StringUtils.hasText(request.getDescription())) {
            putAttribute(attrs, "description", request.getDescription());
        }
        if (StringUtils.hasText(request.getStatus())) {
            putAttribute(attrs, "employeeType", request.getStatus());
        }

        BasicAttribute passwordAttr = new BasicAttribute("userPassword");
        passwordAttr.add(passwordEncoder.encode(passwordSource));
        attrs.put(passwordAttr);

        try {
            ldapTemplate.bind(dn, null, attrs);
        } catch (RuntimeException ex) {
            log.error("Failed to create LDAP user {}", request.getCn(), ex);
            throw new CustomException(CustomErrorCode.LDAP_OPERATION_FAILED, "LDAP 사용자 생성에 실패했습니다.", ex);
        }
    }

    public String reissuePassword(String cn) {
        Name dn = buildUserDn(cn);
        ensureUserExists(dn);
        String newPassword = generateTemporaryPassword();
        BasicAttribute password = new BasicAttribute("userPassword", passwordEncoder.encode(newPassword));
        ModificationItem[] mods = new ModificationItem[]{
                new ModificationItem(DirContext.REPLACE_ATTRIBUTE, password)
        };
        try {
            ldapTemplate.modifyAttributes(dn, mods);
        } catch (RuntimeException ex) {
            log.error("Failed to reset password for {}", cn, ex);
            throw new CustomException(CustomErrorCode.LDAP_OPERATION_FAILED, "비밀번호 재발행에 실패했습니다.", ex);
        }
        return newPassword;
    }

    public void deleteUser(String cn) {
        Name dn = buildUserDn(cn);
        ensureUserExists(dn);
        try {
            ldapTemplate.unbind(dn);
        } catch (RuntimeException ex) {
            log.error("Failed to delete LDAP user {}", cn, ex);
            throw new CustomException(CustomErrorCode.LDAP_OPERATION_FAILED, "LDAP 사용자 삭제에 실패했습니다.", ex);
        }
    }

    private void ensureUserExists(Name dn) {
        try {
            DirContextOperations ctx = ldapTemplate.lookupContext(dn);
            if (ctx == null) {
                throw new CustomException(CustomErrorCode.LDAP_USER_NOT_FOUND);
            }
        } catch (NameNotFoundException ex) {
            throw new CustomException(CustomErrorCode.LDAP_USER_NOT_FOUND);
        }
    }

    private Name buildUserDn(String cn) {
        if (!StringUtils.hasText(cn)) {
            throw new CustomException(CustomErrorCode.LDAP_CN_REQUIRED);
        }
        LdapNameBuilder nameBuilder = StringUtils.hasText(baseDn)
                ? LdapNameBuilder.newInstance(baseDn)
                : LdapNameBuilder.newInstance();

        if (StringUtils.hasText(userBase)) {
            nameBuilder.add(LdapUtils.newLdapName(userBase));
        }
        nameBuilder.add("cn", cn.trim());
        return nameBuilder.build();
    }

    private String resolveSn(LdapUserCreateRequest request) {
        if (StringUtils.hasText(request.getSn())) {
            return request.getSn();
        }
        if (StringUtils.hasText(request.getUid())) {
            return request.getUid();
        }
        return request.getCn();
    }

    private void putAttribute(Attributes attrs, String name, String value) {
        if (StringUtils.hasText(value)) {
            attrs.put(name, value);
        }
    }

    private String generateTemporaryPassword() {
        final String candidates = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@$%?";
        StringBuilder builder = new StringBuilder(passwordLength != null && passwordLength > 0 ? passwordLength : 12);
        int length = passwordLength != null && passwordLength > 0 ? passwordLength : 12;
        for (int i = 0; i < length; i++) {
            int idx = secureRandom.nextInt(candidates.length());
            builder.append(candidates.charAt(idx));
        }
        return builder.toString();
    }

    private LdapUserResponse toResponse(LdapUser user) {
        return LdapUserResponse.builder()
                .cn(user.getCn())
                .uid(user.getUid())
                .uidNumber(user.getUidNumber())
                .mail(user.getMail())
                .ou(user.getOu())
                .description(user.getDescription())
                .gidNumber(user.getGidNumber())
                .homeDirectory(user.getHomeDirectory())
                .status(user.getEmployeeType())
                .build();
    }
}
