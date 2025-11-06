package kr.co.direa.backoffice.service;

import kr.co.direa.backoffice.config.ApprovalProperties;
import kr.co.direa.backoffice.dto.ApproverCandidateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides approver information sourced from configuration and Keycloak.
 */
@Service
@RequiredArgsConstructor
public class ApproverDirectoryService {
    private final ApprovalProperties approvalProperties;
    private final CommonLookupService commonLookupService;

    public List<ApproverCandidateDto> getDefaultApprovers() {
        return approvalProperties.getDefaultApprovers().stream()
                .sorted(Comparator.comparingInt(ApprovalProperties.Stage::getStage))
                .map(this::mapToDto)
                .toList();
    }

    private ApproverCandidateDto mapToDto(ApprovalProperties.Stage stageConfig) {
        Optional<CommonLookupService.KeycloakUserInfo> resolved = resolveUser(stageConfig);

        UUID keycloakId = resolved.map(CommonLookupService.KeycloakUserInfo::id)
                .orElse(stageConfig.getKeycloakId());
        String username = firstNonBlank(
                stageConfig.getUsername(),
                resolved.map(CommonLookupService.KeycloakUserInfo::username).orElse(null)
        );
        String displayName = firstNonBlank(
                stageConfig.getDisplayName(),
                resolved.map(CommonLookupService.KeycloakUserInfo::displayName).orElse(null),
                username
        );
        String label = firstNonBlank(
                stageConfig.getLabel(),
                buildDefaultLabel(stageConfig.getStage())
        );

        return new ApproverCandidateDto(
                stageConfig.getStage(),
                label,
                username,
                displayName,
                keycloakId != null ? keycloakId.toString() : null,
                stageConfig.isLocked()
        );
    }

    private Optional<CommonLookupService.KeycloakUserInfo> resolveUser(ApprovalProperties.Stage stageConfig) {
        Optional<CommonLookupService.KeycloakUserInfo> resolved = Optional.empty();
        if (stageConfig.getKeycloakId() != null) {
            resolved = commonLookupService.resolveKeycloakUserInfoById(stageConfig.getKeycloakId());
        }
        if (resolved.isEmpty() && isNotBlank(stageConfig.getUsername())) {
            resolved = commonLookupService.resolveKeycloakUserInfoByUsername(stageConfig.getUsername());
        }
        return resolved;
    }

    private String buildDefaultLabel(int stage) {
        if (stage <= 0) {
            return "결재자";
        }
        return stage + "차 승인자";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
