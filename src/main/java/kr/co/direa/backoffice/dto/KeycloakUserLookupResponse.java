package kr.co.direa.backoffice.dto;

import kr.co.direa.backoffice.service.CommonLookupService;

public record KeycloakUserLookupResponse(
        boolean lookupAvailable,
        boolean exists,
        String username,
        String displayName,
        String email
) {
    public static KeycloakUserLookupResponse unavailable(String requestedUsername) {
        return new KeycloakUserLookupResponse(false, false, normalize(requestedUsername), null, null);
    }

    public static KeycloakUserLookupResponse found(CommonLookupService.KeycloakUserInfo info, String requestedUsername) {
        String resolvedUsername = info != null ? info.username() : null;
        String displayName = info != null ? info.displayName() : null;
        String email = info != null ? info.email() : null;
        String username = resolvedUsername != null && !resolvedUsername.isBlank()
                ? resolvedUsername
                : normalize(requestedUsername);
        return new KeycloakUserLookupResponse(true, true, username, displayName, email);
    }

    public static KeycloakUserLookupResponse notFound(String requestedUsername) {
        return new KeycloakUserLookupResponse(true, false, normalize(requestedUsername), null, null);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
