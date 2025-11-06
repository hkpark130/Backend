package kr.co.direa.backoffice.vo;

public record DeviceSearchRequest(
                int page,
                int size,
                String filterField,
                String keyword,
                String chipValue
) {

        public DeviceSearchRequest {
                page = page > 0 ? page : 1;
                size = size > 0 ? size : 10;
        }
}
