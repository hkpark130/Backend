package kr.co.direa.backoffice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO representing an approver option exposed to the client.
 */
@Data
@AllArgsConstructor
public class ApproverCandidateDto {
    private int stage;
    private String label;
    private String username;
    private String displayName;
    private String keycloakId;
    private boolean locked;
}
