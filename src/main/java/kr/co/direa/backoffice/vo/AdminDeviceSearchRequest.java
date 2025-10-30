package kr.co.direa.backoffice.vo;

public record AdminDeviceSearchRequest(
        int page,
        int size,
        String filterField,
        String keyword,
        String filterValue,
        String sortField,
        String sortDirection
) {
}
