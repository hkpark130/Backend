package kr.co.direa.backoffice.service;

import kr.co.direa.backoffice.domain.Categories;
import kr.co.direa.backoffice.domain.Departments;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.domain.Users;
import kr.co.direa.backoffice.repository.CategoriesRepository;
import kr.co.direa.backoffice.repository.DepartmentsRepository;
import kr.co.direa.backoffice.repository.ProjectsRepository;
import kr.co.direa.backoffice.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 공통 조회/유틸 모음 서비스.
 * - 도메인 이름/코드 등으로 엔티티를 안전하게 조회 (Optional 반환)
 * - 향후 공통 변환/검증/포맷 함수들을 이곳에 확장 가능합니다.
 */
@Service
@RequiredArgsConstructor
public class CommonLookupService {
    private final CategoriesRepository categoriesRepository;
    private final DepartmentsRepository departmentsRepository;
    private final ProjectsRepository projectsRepository;
    private final UsersRepository usersRepository;

    @Value("${app.keycloak.url:}")
    private String keycloakUrl;
    @Value("${app.keycloak.realm:}")
    private String keycloakRealm;
    @Value("${app.keycloak.admin-client-id:}")
    private String keycloakAdminClientId;
    @Value("${app.keycloak.admin-client-secret:}")
    private String keycloakAdminClientSecret;

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

    public Optional<Users> findUserByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        return usersRepository.findByUsername(username);
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
        if (username == null || username.isBlank()) return Optional.empty();
        if (isBlank(keycloakUrl) || isBlank(keycloakRealm) || isBlank(keycloakAdminClientId) || isBlank(keycloakAdminClientSecret)) {
            return Optional.empty();
        }
        try {
            String token = getKeycloakAdminAccessToken().orElse(null);
            if (token == null) return Optional.empty();

            String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
            String url = keycloakUrl + "/admin/realms/" + keycloakRealm + "/users?username=" + encoded + "&exact=true";

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
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null || resp.getBody().isEmpty()) return Optional.empty();

            Map<String,Object> first = resp.getBody().get(0);
            Object id = first.get("id");
            if (id != null) return Optional.of(id.toString());
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private Optional<String> getKeycloakAdminAccessToken() {
        try {
            String url = keycloakUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", keycloakAdminClientId);
            form.add("client_secret", keycloakAdminClientSecret);

            HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(form, headers);
        RestTemplate rt = new RestTemplate();
        ResponseEntity<Map<String,Object>> resp = rt.exchange(
            url,
            HttpMethod.POST,
            req,
            new ParameterizedTypeReference<Map<String,Object>>() {}
        );
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) return Optional.empty();
        Object token = resp.getBody().get("access_token");
            return Optional.ofNullable(token).map(Object::toString).filter(s -> !s.isBlank());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
