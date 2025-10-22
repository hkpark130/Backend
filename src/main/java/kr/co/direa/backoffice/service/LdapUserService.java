package kr.co.direa.backoffice.service;

import javax.naming.Name;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import kr.co.direa.backoffice.dto.ldap.LdapUserCreateRequest;
import kr.co.direa.backoffice.dto.ldap.LdapUserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.ldap.support.LdapUtils;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.security.SecureRandom;
import java.util.List;

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
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Value("${spring.ldap.base:}")
    private String baseDn;

    @Value("${app.ldap.user-base:ou=people}")
    private String userBase;

    @Value("${app.ldap.login-shell:/bin/bash}")
    private String loginShell;

    @Value("${app.ldap.password-length:12}")
    private Integer passwordLength;

    public List<LdapUserResponse> fetchUsers() {
        LdapQueryBuilder builder = LdapQueryBuilder.query();
        if (StringUtils.hasText(userBase)) {
            builder = builder.base(userBase);
        }
    LdapQuery query = builder.where("objectClass").is("inetOrgPerson");
        return ldapTemplate.search(query, (AttributesMapper<LdapUserResponse>) attrs -> LdapUserResponse.builder()
                .cn(getAttribute(attrs, "cn"))
                .uid(getAttribute(attrs, "uid"))
                .uidNumber(parseInteger(getAttribute(attrs, "uidNumber")))
                .mail(getAttribute(attrs, "mail"))
                .ou(getAttribute(attrs, "ou"))
                .description(getAttribute(attrs, "description"))
                .gidNumber(parseInteger(getAttribute(attrs, "gidNumber")))
                .homeDirectory(getAttribute(attrs, "homeDirectory"))
                .status(getAttribute(attrs, "employeeType"))
                .build());
    }

    public boolean isCnAvailable(String cn) {
        if (!StringUtils.hasText(cn)) {
            return false;
        }
        return !existsByAttribute("cn", cn.trim());
    }

    public boolean isUidNumberAvailable(Integer uidNumber) {
        if (uidNumber == null) {
            return false;
        }
        return !existsByAttribute("uidNumber", String.valueOf(uidNumber));
    }

    public void createUser(LdapUserCreateRequest request) {
        if (!isCnAvailable(request.getCn())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 cn 입니다.");
        }
        if (!isUidNumberAvailable(request.getUidNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 uidNumber 입니다.");
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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "LDAP 사용자 생성에 실패했습니다.", ex);
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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "비밀번호 재발행에 실패했습니다.", ex);
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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "LDAP 사용자 삭제에 실패했습니다.", ex);
        }
    }

    private boolean existsByAttribute(String attribute, String value) {
        LdapQueryBuilder builder = LdapQueryBuilder.query();
        if (StringUtils.hasText(userBase)) {
            builder = builder.base(userBase);
        }
    LdapQuery query = builder
        .where("objectClass").is("inetOrgPerson")
        .and(attribute).is(value);
    List<String> results = ldapTemplate.search(query, (AttributesMapper<String>) attrs -> value);
        return !results.isEmpty();
    }

    private void ensureUserExists(Name dn) {
        try {
            DirContextOperations ctx = ldapTemplate.lookupContext(dn);
            if (ctx == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
            }
        } catch (NameNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
    }

    private Name buildUserDn(String cn) {
        if (!StringUtils.hasText(cn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cn은 필수입니다.");
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

    private String getAttribute(Attributes attrs, String key) {
        Attribute attribute = attrs.get(key);
        if (attribute == null) {
            return null;
        }
        try {
            Object value = attribute.get();
            return value != null ? value.toString() : null;
        } catch (Exception ex) {
            log.warn("Failed to read ldap attribute {}", key, ex);
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            log.debug("Unable to parse integer from [{}]", value, ex);
            return null;
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
}
