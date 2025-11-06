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
        public AdminDeviceSearchRequest {
                page = page > 0 ? page : 1;
                size = size > 0 ? size : 10;
                filterField = hasText(filterField) ? filterField : "categoryName";
                sortField = hasText(sortField) ? sortField : "categoryName";
                sortDirection = hasText(sortDirection) ? sortDirection : "asc";
        }

        private static boolean hasText(String value) {
                return value != null && !value.isBlank();
        }
}
