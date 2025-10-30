package kr.co.direa.backoffice.vo;

public record ApprovalSearchRequest(
        int page,
        int size,
        String filterField,
        String keyword,
        String chipValue,
        String sortField,
        String sortOrder
) {
}
