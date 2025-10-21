package kr.co.direa.backoffice.dto;

import kr.co.direa.backoffice.domain.Categories;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
public class CategoryDto implements Serializable {
    private Long id;
    private String name;
    private String img;

    public CategoryDto(Categories entity) {
        this.id = entity.getId();
        this.name = entity.getName();
        this.img = entity.getImg();
    }
}
