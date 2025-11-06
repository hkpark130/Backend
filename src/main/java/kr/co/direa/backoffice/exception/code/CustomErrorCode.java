package kr.co.direa.backoffice.exception.code;

import org.springframework.http.HttpStatus;

public enum CustomErrorCode {
    DEPARTMENT_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "DEPT-001", "부서 이름을 입력해 주세요."),
    DEPARTMENT_NAME_DUPLICATED(HttpStatus.CONFLICT, "DEPT-002", "이미 존재하는 부서 이름입니다."),
    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DEPT-003", "존재하지 않는 부서입니다."),
    DEPARTMENT_DELETE_CONFLICT(HttpStatus.CONFLICT, "DEPT-004", "연관된 데이터가 있어 삭제할 수 없습니다."),
    REQUESTED_DEPARTMENT_NOT_FOUND(HttpStatus.BAD_REQUEST, "DEPT-005", "존재하지 않는 관리부서입니다."),

    CATEGORY_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "CAT-001", "카테고리 이름을 입력해 주세요."),

    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRJ-001", "존재하지 않는 프로젝트입니다."),
    PROJECT_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "PRJ-002", "프로젝트 이름을 입력해 주세요."),
    PROJECT_CODE_REQUIRED(HttpStatus.BAD_REQUEST, "PRJ-003", "프로젝트 코드를 입력해 주세요."),
    PROJECT_DELETE_CONFLICT(HttpStatus.CONFLICT, "PRJ-004", "연관된 데이터가 있어 삭제할 수 없습니다."),

    APPROVAL_TYPE_UNSUPPORTED(HttpStatus.BAD_REQUEST, "APR-001", "지원하지 않는 결재 유형입니다."),
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "DEV-001", "해당 장비를 찾을 수 없습니다."),
    DEVICE_USER_NOT_IDENTIFIED(HttpStatus.UNAUTHORIZED, "DEV-002", "인증된 사용자 정보를 확인할 수 없습니다."),
    DEVICE_NOT_OWNED_BY_USER(HttpStatus.FORBIDDEN, "DEV-003", "요청한 사용자의 장비가 아닙니다."),
    DEVICE_RECOVERY_NOT_DISPOSED(HttpStatus.CONFLICT, "DEV-004", "폐기된 장비만 복구할 수 있습니다."),
    DEVICE_APPLICANT_NOT_FOUND(HttpStatus.NOT_FOUND, "DEV-005", "신청자 정보를 찾을 수 없습니다."),
    DEVICE_OPERATOR_NOT_FOUND(HttpStatus.NOT_FOUND, "DEV-006", "처리자 정보를 찾을 수 없습니다."),
    APPROVAL_USAGE_PERIOD_INCOMPLETE(HttpStatus.BAD_REQUEST, "APR-002", "사용 기간의 시작일과 종료일을 모두 입력해 주세요."),
    APPROVAL_USAGE_END_BEFORE_START(HttpStatus.BAD_REQUEST, "APR-003", "사용 종료일은 시작일 이후여야 합니다."),
    APPROVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "APR-004", "Approval not found."),
    APPROVER_USERNAME_REQUIRED(HttpStatus.BAD_REQUEST, "APR-005", "Approver username must not be empty."),
    APPROVER_LIST_REQUIRED(HttpStatus.BAD_REQUEST, "APR-006", "Approver list must not be empty."),
    APPROVAL_STEP_NOT_ASSIGNED(HttpStatus.FORBIDDEN, "APR-007", "Approver not assigned to this approval."),
    APPROVAL_STEP_ALREADY_DECIDED(HttpStatus.CONFLICT, "APR-008", "Approval already processed for this step."),
    APPROVAL_NO_AVAILABLE_APPROVER(HttpStatus.BAD_REQUEST, "APR-009", "No approvers available for device approval workflow."),
    APPROVAL_ALREADY_COMPLETED(HttpStatus.CONFLICT, "APR-010", "이미 처리된 결재는 취소할 수 없습니다."),
    APPROVAL_CANCEL_FORBIDDEN(HttpStatus.FORBIDDEN, "APR-011", "본인 신청만 취소할 수 있습니다."),
    APPROVAL_UPDATE_COMPLETED(HttpStatus.CONFLICT, "APR-012", "처리 완료된 결재는 수정할 수 없습니다."),
    APPROVAL_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN, "APR-013", "본인 신청만 수정할 수 있습니다."),
    APPROVAL_UPDATE_EMPTY(HttpStatus.BAD_REQUEST, "APR-014", "수정할 정보를 전달해 주세요."),
    APPROVAL_UPDATE_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "APR-015", "수정할 사유를 입력해 주세요."),
    APPROVAL_PREVIOUS_STEP_INCOMPLETE(HttpStatus.CONFLICT, "APR-016", "Previous approval steps are not completed."),
    APPROVAL_REQUESTER_UUID_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "APR-017", "요청자 Keycloak UUID를 설정할 수 없습니다."),
    APPROVAL_PENDING_DUPLICATE(HttpStatus.CONFLICT, "APR-018", "이미 처리 중인 반납/폐기 신청이 있습니다. 기존 신청을 완료해 주세요."),
    APPROVAL_ALREADY_TERMINATED(HttpStatus.CONFLICT, "APR-019", "이미 완료되거나 취소된 결재입니다."),

    COMMENT_CONTENT_EMPTY(HttpStatus.BAD_REQUEST, "CMT-001", "Comment content must not be empty."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CMT-002", "Approval comment not found."),
    COMMENT_APPROVAL_MISMATCH(HttpStatus.BAD_REQUEST, "CMT-003", "Comment does not belong to approval."),
    COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "CMT-004", "You do not have permission to modify this comment."),

    USERNAME_REQUIRED(HttpStatus.BAD_REQUEST, "USR-001", "Username must not be empty."),

    NOTIFICATION_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "NTF-001", "해당 알림을 삭제할 권한이 없습니다."),

    LDAP_CN_CONFLICT(HttpStatus.CONFLICT, "LDAP-001", "이미 존재하는 cn 입니다."),
    LDAP_UID_CONFLICT(HttpStatus.CONFLICT, "LDAP-002", "이미 사용 중인 uidNumber 입니다."),
    LDAP_CN_REQUIRED(HttpStatus.BAD_REQUEST, "LDAP-003", "cn은 필수입니다."),
    LDAP_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "LDAP-004", "사용자를 찾을 수 없습니다."),
    LDAP_OPERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "LDAP-005", "LDAP 작업을 처리하지 못했습니다."),

    COMMON_BAD_REQUEST(HttpStatus.BAD_REQUEST, "COM-001", "잘못된 요청입니다."),
    COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "COM-002", "요청한 리소스를 찾을 수 없습니다."),
    COMMON_CONFLICT(HttpStatus.CONFLICT, "COM-003", "요청을 처리할 수 없습니다."),
    COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COM-999", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CustomErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
