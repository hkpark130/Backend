package kr.co.direa.backoffice.vo;

import lombok.Data;

@Data
public class ApprovalActionRequest {
    private String approverUsername;
    private String comment;
}
