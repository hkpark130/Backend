package kr.co.direa.backoffice.dto;

import kr.co.direa.backoffice.domain.Departments;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
public class DepartmentDto implements Serializable {
    private Long id;
    private String name;

    public DepartmentDto(Departments entity) {
        this.id = entity.getId();
        this.name = entity.getName();
    }
}
