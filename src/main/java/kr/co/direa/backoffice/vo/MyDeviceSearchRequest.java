package kr.co.direa.backoffice.vo;

public record MyDeviceSearchRequest(int page,
                                    int size,
                                    String filterField,
                                    String keyword,
                                    String chipValue,
                                    String sortField,
                                    String sortDirection) {

    public MyDeviceSearchRequest {
        page = page > 0 ? page : 1;
        size = size > 0 ? size : 10;
        filterField = hasText(filterField) ? filterField : "all";
        sortField = hasText(sortField) ? sortField : "categoryName";
        sortDirection = hasText(sortDirection) ? sortDirection : "asc";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
