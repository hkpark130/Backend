package kr.co.direa.backoffice.security;

import kr.co.direa.backoffice.service.CommonLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 중앙에서 관리자 권한 여부를 판별하기 위한 보조 컴포넌트.
 * JWT 클레임 또는 권한 목록에 Admin 그룹이 존재하는지 우선 확인하고,
 * 없을 경우 Keycloak Admin API 조회를 통해 최종 판정한다.
 */
@Component("adminAuthorization")
@RequiredArgsConstructor
public class AdminAuthorization {
    private static final String ADMIN_GROUP_NAME = "Admin";
    private final CommonLookupService commonLookupService;

    public boolean hasAdminAccess() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }

        if (hasAdminAuthority(authentication)) {
            return true;
        }

        Object principal = authentication.getPrincipal();
        String preferredUsername = null;
        if (principal instanceof Jwt jwt) {
            if (hasAdminGroupClaim(jwt)) {
                return true;
            }
            preferredUsername = safeTrim(jwt.getClaimAsString("preferred_username"));
            if (preferredUsername == null) {
                preferredUsername = safeTrim(jwt.getClaimAsString("preferredUsername"));
            }
        }

        if (preferredUsername == null) {
            preferredUsername = commonLookupService.currentUsernameFromJwt()
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .orElse(null);
        }

    return preferredUsername != null && commonLookupService.isAdminUser(preferredUsername);
    }

    private boolean hasAdminAuthority(Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities == null) {
            return false;
        }
    return authorities.stream()
        .filter(Objects::nonNull)
        .map(GrantedAuthority::getAuthority)
        .anyMatch(this::matchesAdminIdentifier);
    }

    private boolean hasAdminGroupClaim(Jwt jwt) {
        if (jwt == null) {
            return false;
        }

        Object groups = jwt.getClaim("groups");
        if (groups instanceof Collection<?> collection) {
            for (Object entry : collection) {
                if (matchesAdminIdentifier(entry)) {
                    return true;
                }
            }
        }

        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object roles = realmAccess.get("roles");
            if (roles instanceof Collection<?> roleCollection) {
                for (Object role : roleCollection) {
                    if (matchesAdminIdentifier(role)) {
                        return true;
                    }
                }
            }
        }

        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null) {
            for (Object resource : resourceAccess.values()) {
                if (resource instanceof Map<?, ?> resourceMap) {
                    Object roles = resourceMap.get("roles");
                    if (roles instanceof Collection<?> roleCollection) {
                        for (Object role : roleCollection) {
                            if (matchesAdminIdentifier(role)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean matchesAdminIdentifier(Object candidate) {
        if (!(candidate instanceof String value)) {
            return false;
        }
        String normalized = safeTrim(value);
        if (normalized == null || normalized.isEmpty()) {
            return false;
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash + 1 < normalized.length()) {
            normalized = normalized.substring(lastSlash + 1);
        }
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
    return ADMIN_GROUP_NAME.equalsIgnoreCase(normalized);
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }
}
