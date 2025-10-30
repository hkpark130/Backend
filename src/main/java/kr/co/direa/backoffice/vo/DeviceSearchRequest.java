package kr.co.direa.backoffice.vo;

public record DeviceSearchRequest(
        int page,
        int size,
        String filterField,
        String keyword,
        String chipValue
) {
}
