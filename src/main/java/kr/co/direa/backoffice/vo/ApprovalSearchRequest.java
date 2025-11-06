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

        public ApprovalSearchRequest {
                page = page > 0 ? page : 1;
                size = size > 0 ? size : 10;
        }
}
