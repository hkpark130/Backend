package kr.co.direa.backoffice.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.co.direa.backoffice.repository.CategoriesRepository;
import kr.co.direa.backoffice.repository.DepartmentsRepository;
import kr.co.direa.backoffice.repository.ProjectsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class CommonLookupServiceTest {

    @Mock
    private CategoriesRepository categoriesRepository;

    @Mock
    private DepartmentsRepository departmentsRepository;

    @Mock
    private ProjectsRepository projectsRepository;

    private CommonLookupService commonLookupService;

    @BeforeEach
    void setUp() {
        commonLookupService = new CommonLookupService(categoriesRepository, departmentsRepository, projectsRepository);
    ReflectionTestUtils.setField(commonLookupService, "keycloakUrl", loadYamlProperty("constants.keycloak-url"));
        ReflectionTestUtils.setField(commonLookupService, "keycloakRealm", "sso");
        ReflectionTestUtils.setField(commonLookupService, "keycloakAdminClientId", "admin-cli");
        ReflectionTestUtils.setField(commonLookupService, "keycloakAdminUsername", "admin");
        ReflectionTestUtils.setField(commonLookupService, "keycloakAdminPassword", "admin");
    }

    @Test
    void resolveKeycloakUserInfoByUsername_returnsEmailAddressFromKeycloak() {
        UUID expectedId = UUID.randomUUID();
        Map<String, Object> userPayload = new HashMap<>();
        userPayload.put("id", expectedId.toString());
        userPayload.put("username", "박현경");
        userPayload.put("email", "parkhk@direa.co.kr");
        userPayload.put("firstName", "현경");
        userPayload.put("lastName", "박");

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class, (mock, context) -> {
            Mockito.when(mock.exchange(anyString(), any(HttpMethod.class), Mockito.<HttpEntity<?>>any(), Mockito.<ParameterizedTypeReference<Object>>any()))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    ParameterizedTypeReference<?> typeRef = invocation.getArgument(3);
                    if (url.contains("/protocol/openid-connect/token")) {
                        Map<String, Object> tokenBody = Map.of("access_token", "dummy-token");
                        return new ResponseEntity<>((Object) tokenBody, HttpStatus.OK);
                    }
                    if (url.contains("/users")) {
                        List<Map<String, Object>> body = List.of(userPayload);
                        return new ResponseEntity<>((Object) body, HttpStatus.OK);
                    }
                    Object emptyBody = typeRef.getType().getTypeName().contains("List") ? List.of() : Map.of();
                    return new ResponseEntity<>(emptyBody, HttpStatus.OK);
                });
        })) {
            Optional<CommonLookupService.KeycloakUserInfo> result = commonLookupService.resolveKeycloakUserInfoByUsername("박현경");

            assertThat(result).isPresent();
            assertThat(result.get().email()).isEqualTo("parkhk@direa.co.kr");
            assertThat(result.get().id()).isEqualTo(expectedId);
            assertThat(result.get().displayName()).isEqualTo("박현경");
        }
    }

    private String loadYamlProperty(String key) {
        Yaml yaml = new Yaml();
        String[] segments = key.split("\\.");
        ClassPathResource resource = new ClassPathResource("application.yml");
        try (InputStream inputStream = resource.getInputStream()) {
            Iterable<Object> documents = yaml.loadAll(inputStream);
            for (Object document : documents) {
                if (document instanceof Map<?, ?> map) {
                    Object value = lookupNestedValue(map, segments, 0);
                    if (value != null) {
                        return value.toString();
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private Object lookupNestedValue(Map<?, ?> source, String[] segments, int index) {
        if (index >= segments.length) {
            return source;
        }
        Object current = source.get(segments[index]);
        if (current == null) {
            return null;
        }
        if (index == segments.length - 1) {
            return current;
        }
        if (current instanceof Map<?, ?> nested) {
            return lookupNestedValue(nested, segments, index + 1);
        }
        return null;
    }
}