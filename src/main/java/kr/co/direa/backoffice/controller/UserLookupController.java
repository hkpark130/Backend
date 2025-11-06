package kr.co.direa.backoffice.controller;

import kr.co.direa.backoffice.dto.KeycloakUserLookupResponse;
import kr.co.direa.backoffice.service.CommonLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class UserLookupController {

    private final CommonLookupService commonLookupService;

    @GetMapping("/admin/keycloak-users/lookup")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<KeycloakUserLookupResponse> lookupKeycloakUser(@RequestParam("username") String username) {
        String normalized = username == null ? null : username.trim();
        if (normalized == null || normalized.isEmpty()) {
            return ResponseEntity.ok(KeycloakUserLookupResponse.notFound(null));
        }

        if (!commonLookupService.isKeycloakLookupAvailable()) {
            return ResponseEntity.ok(KeycloakUserLookupResponse.unavailable(normalized));
        }

        return ResponseEntity.ok(
                commonLookupService.resolveKeycloakUserInfoByUsername(normalized)
                        .map(info -> KeycloakUserLookupResponse.found(info, normalized))
                        .orElseGet(() -> KeycloakUserLookupResponse.notFound(normalized))
        );
    }
}
