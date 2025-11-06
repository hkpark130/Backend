package kr.co.direa.backoffice.service;

import kr.co.direa.backoffice.domain.Categories;
import kr.co.direa.backoffice.domain.Departments;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.repository.CategoriesRepository;
import kr.co.direa.backoffice.repository.DepartmentsRepository;
import kr.co.direa.backoffice.repository.ProjectsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 공통 조회/유틸 모음 서비스.
 * - 도메인 이름/코드 등으로 엔티티를 안전하게 조회 (Optional 반환)
 * - 향후 공통 변환/검증/포맷 함수들을 이곳에 확장 가능합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommonLookupService {
    private final CategoriesRepository categoriesRepository;
    private final DepartmentsRepository departmentsRepository;
    private final ProjectsRepository projectsRepository;

    @Value("${app.keycloak.url:}")
    private String keycloakUrl;
    @Value("${app.keycloak.realm:}")
    private String keycloakRealm;
    @Value("${app.keycloak.admin-client-id:}")
    private String keycloakAdminClientId;
    @Value("${app.keycloak.admin-client-secret:}")
    private String keycloakAdminClientSecret;
    @Value("${constants.admin:}")
    private String keycloakAdminUsername;
    @Value("${constants.admin-id:}")
    private String keycloakAdminUserId;
    @Value("${constants.admin-pw:}")
    private String keycloakAdminPassword;
    @Value("${constants.admin-group-id:}")
    private String keycloakAdminGroupId;

    public Optional<Categories> findCategoryByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return Optional.ofNullable(categoriesRepository.findByName(name));
    }

    public Optional<Departments> findDepartmentByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return departmentsRepository.findByName(name);
    }

    public Optional<Projects> findProjectByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return Optional.ofNullable(projectsRepository.findByName(name));
    }

    /**
     * JWT(인증된 요청)에서 현재 사용자의 Keycloak UUID(sub) 추출
     */
    public Optional<String> currentUserIdFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Optional.empty();
        Object principal = auth.getPrincipal();
        return extractClaimSafely(principal, "sub");
    }

    /**
     * JWT(인증된 요청)에서 현재 사용자의 username(preferred_username) 추출
     */
    public Optional<String> currentUsernameFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Optional.empty();
        Object principal = auth.getPrincipal();
        return extractClaimSafely(principal, "preferred_username");
    }

    public Optional<String> currentUserEmailFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        return extractClaimSafely(principal, "email");
    }

    public Optional<String> currentUserDisplayNameFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        Optional<String> directName = extractClaimSafely(principal, "name");
        if (directName.isPresent()) {
            return directName;
        }
        Optional<String> given = extractClaimSafely(principal, "given_name");
        Optional<String> family = extractClaimSafely(principal, "family_name");
        if (given.isPresent() || family.isPresent()) {
            String combined = combineNameParts(family.orElse(null), given.orElse(null));
            if (combined != null) {
                return Optional.of(combined);
            }
        }
        return Optional.empty();
    }

    public boolean isKeycloakLookupAvailable() {
        return !isBlank(keycloakUrl) && !isBlank(keycloakRealm) && !isBlank(keycloakAdminClientId);
    }

    private Optional<String> extractClaimSafely(Object principal, String claimName) {
        if (principal == null) return Optional.empty();
        try {
            // 기대 타입: org.springframework.security.oauth2.jwt.Jwt
            var method = principal.getClass().getMethod("getClaims");
            Object claims = method.invoke(principal);
            if (claims instanceof java.util.Map<?, ?> map) {
                Object v = map.get(claimName);
                if (v == null) return Optional.empty();
                String s = v.toString();
                return s.isBlank() ? Optional.empty() : Optional.of(s);
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    /**
     * username으로 Keycloak UUID 조회 (Admin API 연동 필요)
     * - 추후 Keycloak Admin Client/REST 연동 후 구현
     */
    public Optional<String> resolveKeycloakUserIdByUsername(String username) {
        return resolveKeycloakUserInfoByUsername(username)
                .map(KeycloakUserInfo::id)
                .map(UUID::toString);
    }

    public Optional<KeycloakUserInfo> resolveKeycloakUserInfoByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
    if (!isBlank(keycloakAdminUsername)
        && username.equalsIgnoreCase(keycloakAdminUsername)
        && !isBlank(keycloakAdminUserId)) {
            return parseUuid(keycloakAdminUserId)
                    .map(adminId -> new KeycloakUserInfo(adminId, keycloakAdminUsername, null, keycloakAdminUsername));
        }
        if (isBlank(keycloakUrl) || isBlank(keycloakRealm) || isBlank(keycloakAdminClientId)) {
            return Optional.empty();
        }
        try {
            String token = getKeycloakAdminAccessToken().orElse(null);
            if (token == null) {
                return Optional.empty();
            }

            String url = keycloakUrl + "/admin/realms/" + keycloakRealm + "/users?username=" + username + "&exact=true";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            RestTemplate rt = new RestTemplate();
            ResponseEntity<List<Map<String,Object>>> resp = rt.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<Map<String,Object>>>() {}
            );
            log.info("status={}, headers={}, body={}", resp.getStatusCode(), resp.getHeaders(), resp.getBody());
            List<Map<String,Object>> body = resp.getBody();
            if (!resp.getStatusCode().is2xxSuccessful() || body == null || body.isEmpty()) {
                return Optional.empty();
            }

            Map<String,Object> first = body.get(0);
            UUID externalId = parseUuid(toStringOrNull(first.get("id"))).orElse(null);
            String resolvedUsername = toStringOrNull(first.get("username"));
            String email = toStringOrNull(first.get("email"));
            if (resolvedUsername == null || resolvedUsername.isBlank()) {
                resolvedUsername = username;
            }
            String displayName = extractDisplayName(first, resolvedUsername);
            return Optional.of(new KeycloakUserInfo(externalId, resolvedUsername, email, displayName));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public KeycloakUserInfo fallbackUserInfo(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return new KeycloakUserInfo(null, trimmed, null, trimmed);
    }

    public Optional<KeycloakUserInfo> resolveKeycloakUserInfoById(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        if (isBlank(keycloakUrl) || isBlank(keycloakRealm)) {
            return Optional.empty();
        }
        String token = getKeycloakAdminAccessToken().orElse(null);
        if (token == null) {
            return Optional.empty();
        }
        try {
            String url = keycloakUrl + "/admin/realms/" + keycloakRealm + "/users/" + userId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            RestTemplate rt = new RestTemplate();
            ResponseEntity<Map<String,Object>> resp = rt.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String,Object>>() {}
            );
            Map<String,Object> body = resp.getBody();
            if (!resp.getStatusCode().is2xxSuccessful() || body == null) {
                return Optional.empty();
            }

            UUID externalId = parseUuid(toStringOrNull(body.get("id"))).orElse(userId);
            String username = toStringOrNull(body.get("username"));
            String email = toStringOrNull(body.get("email"));
            String displayName = extractDisplayName(body, username);
            return Optional.of(new KeycloakUserInfo(externalId, username, email, displayName));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /**
     * 주어진 사용자가 Keycloak Admin 그룹에 속하는지 확인합니다.
     * - admin 기본 계정(username)이면 바로 true
     * - 그룹 ID 설정 및 Keycloak Admin API가 정상 동작할 경우 그룹 소속 여부를 조회
     */
    public boolean isAdminUser(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        if (isBlank(keycloakAdminGroupId)) {
            return false;
        }
        var userInfo = resolveKeycloakUserInfoByUsername(username).orElse(null);
        if (userInfo == null || userInfo.id() == null) {
            return false;
        }
        return isUserInGroup(userInfo.id().toString(), keycloakAdminGroupId);
    }

    private Optional<String> getKeycloakAdminAccessToken() {
        if (isBlank(keycloakUrl) || isBlank(keycloakRealm)) {
            return Optional.empty();
        }
        if (isBlank(keycloakAdminClientId)) {
            return Optional.empty();
        }
        try {
            String url = keycloakUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            boolean usingPasswordGrant = !isBlank(keycloakAdminUsername) && !isBlank(keycloakAdminPassword);
            if (usingPasswordGrant) {
                form.add("grant_type", "password");
                form.add("client_id", keycloakAdminClientId);
                form.add("username", keycloakAdminUsername);
                form.add("password", keycloakAdminPassword);
            } else {
                if (isBlank(keycloakAdminClientSecret)) {
                    return Optional.empty();
                }
                form.add("grant_type", "client_credentials");
                form.add("client_id", keycloakAdminClientId);
                form.add("client_secret", keycloakAdminClientSecret);
            }

            HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(form, headers);
            RestTemplate rt = new RestTemplate();
            ResponseEntity<Map<String,Object>> resp = rt.exchange(
                    url,
                    HttpMethod.POST,
                    req,
                    new ParameterizedTypeReference<Map<String,Object>>() {}
            );
            Map<String,Object> body = resp.getBody();
            if (!resp.getStatusCode().is2xxSuccessful() || body == null) {
                return Optional.empty();
            }
            Object token = body.get("access_token");
            return Optional.ofNullable(token).map(Object::toString).filter(s -> !s.isBlank());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private boolean isUserInGroup(String userId, String groupId) {
        if (isBlank(userId) || isBlank(groupId)) {
            return false;
        }
        if (isBlank(keycloakUrl) || isBlank(keycloakRealm)) {
            return false;
        }
        String token = getKeycloakAdminAccessToken().orElse(null);
        if (token == null) {
            return false;
        }
        try {
            String url = keycloakUrl + "/admin/realms/" + keycloakRealm + "/users/" + userId + "/groups";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            RestTemplate rt = new RestTemplate();
            ResponseEntity<List<Map<String, Object>>> resp = rt.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> body = resp.getBody();
            if (!resp.getStatusCode().is2xxSuccessful() || body == null) {
                return false;
            }
            boolean matched = body.stream()
                    .map(group -> group.get("id"))
                    .filter(id -> id != null)
                    .anyMatch(id -> groupId.equals(id.toString()));
            return matched;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private String toStringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String str = value.toString();
        return str.isBlank() ? null : str;
    }

    private String extractDisplayName(Map<String, Object> source, String fallback) {
        if (source == null) {
            return fallback;
        }
        String lastName = toStringOrNull(source.get("lastName"));
        String firstName = toStringOrNull(source.get("firstName"));
        String combined = combineNameParts(lastName, firstName);
        if (combined != null) {
            return combined;
        }

        Object attributes = source.get("attributes");
        if (attributes instanceof Map<?,?> attrMap) {
            Object displayAttr = attrMap.get("displayName");
            String attrValue = extractAttributeValue(displayAttr);
            if (isNotBlank(attrValue)) {
                return attrValue;
            }
        }
        return isBlank(fallback) ? null : fallback;
    }

    private String combineNameParts(String lastName, String firstName) {
        StringBuilder builder = new StringBuilder();
        if (isNotBlank(lastName)) {
            builder.append(lastName);
        }
        if (isNotBlank(firstName)) {
            builder.append(firstName);
        }
        return builder.length() > 0 ? builder.toString() : null;
    }

    private String extractAttributeValue(Object attribute) {
        if (attribute == null) {
            return null;
        }
        if (attribute instanceof List<?> list && !list.isEmpty()) {
            return toStringOrNull(list.get(0));
        }
        return toStringOrNull(attribute);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    public record KeycloakUserInfo(UUID id, String username, String email, String displayName) {}
}
