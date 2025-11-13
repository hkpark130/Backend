package kr.co.direa.backoffice.repository.spec;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import jakarta.persistence.criteria.*;

import kr.co.direa.backoffice.constant.Constants;
import kr.co.direa.backoffice.domain.Devices;
import org.springframework.data.jpa.domain.Specification;

public final class DeviceSpecifications {
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.systemDefault();

    private DeviceSpecifications() {
    }

    public static Specification<Devices> adminSearch(AdminDeviceSearchContext context) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }

            Predicate disposalPredicate = buildDisposalPredicate(context.disposedOnly(), root, query, cb);
            Predicate filterPredicate = buildFilterPredicate(context, root, query, cb);
            Predicate keywordPredicate = buildKeywordPredicate(context, root, query, cb);

            return cb.and(disposalPredicate, filterPredicate, keywordPredicate);
        };
    }

    public static Specification<Devices> adminDisposalOnly(boolean disposedOnly) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            return buildDisposalPredicate(disposedOnly, root, query, cb);
        };
    }

    private static Predicate buildDisposalPredicate(boolean disposedOnly,
                                                    Root<Devices> root,
                                                    CriteriaQuery<?> query,
                                                    CriteriaBuilder cb) {
        Predicate statusIsDisposed = cb.equal(root.get("status"), Constants.DISPOSE_TYPE);

        if (disposedOnly) {
            return statusIsDisposed;
        }

        return cb.not(statusIsDisposed);
    }

    private static Predicate buildFilterPredicate(AdminDeviceSearchContext context,
                                                  Root<Devices> root,
                                                  CriteriaQuery<?> query,
                                                  CriteriaBuilder cb) {
        String value = normalize(context.filterValue());
        String field = normalize(context.filterField());
        if (value == null || field == null) {
            return cb.conjunction();
        }

        return switch (field) {
            case "categoryName" -> cb.equal(resolveCategoryName(root), value);
            case "id" -> cb.equal(root.get("id"), value);
            case "username" -> cb.equal(trimExpression(root.get("realUser"), cb), value);
            case "status" -> cb.equal(root.get("status"), value);
            case "manageDepName" -> cb.equal(resolveManageDepartmentName(root), value);
            case "projectName" -> cb.equal(resolveProjectName(root), value);
            case "purpose" -> cb.equal(root.get("purpose"), value);
            case "company" -> cb.equal(root.get("company"), value);
            case "model" -> cb.equal(root.get("model"), value);
            case "description" -> cb.equal(root.get("description"), value);
            case "purchaseDate" -> buildPurchaseDatePredicate(root, cb, value);
            default -> cb.conjunction();
        };
    }

    private static Predicate buildKeywordPredicate(AdminDeviceSearchContext context,
                                                   Root<Devices> root,
                                                   CriteriaQuery<?> query,
                                                   CriteriaBuilder cb) {
        String rawKeyword = normalize(context.keyword());
        if (rawKeyword == null) {
            return cb.conjunction();
        }
        String lowered = "%" + rawKeyword.toLowerCase(Locale.ROOT) + "%";

        String field = normalize(context.filterField());
        if (field == null || "all".equals(field)) {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(likeExpression(root.get("id"), lowered, cb));
            predicates.add(likeExpression(resolveCategoryName(root), lowered, cb));
            predicates.add(likeExpression(resolveManageDepartmentName(root), lowered, cb));
            predicates.add(likeExpression(resolveProjectName(root), lowered, cb));
            predicates.add(likeExpression(root.get("purpose"), lowered, cb));
            predicates.add(likeExpression(root.get("company"), lowered, cb));
            predicates.add(likeExpression(root.get("model"), lowered, cb));
            predicates.add(likeExpression(root.get("description"), lowered, cb));
            predicates.add(likeExpression(root.get("sn"), lowered, cb));
            predicates.add(likeExpression(root.get("status"), lowered, cb));
            predicates.add(likeExpression(normalizeExpression(root.get("realUser"), cb), lowered, cb));
            return cb.or(predicates.stream().filter(Objects::nonNull).toArray(Predicate[]::new));
        }

        Expression<String> target = switch (field) {
            case "categoryName" -> resolveCategoryName(root);
            case "id" -> root.get("id");
            case "username" -> normalizeExpression(root.get("realUser"), cb);
            case "status" -> root.get("status");
            case "manageDepName" -> resolveManageDepartmentName(root);
            case "projectName" -> resolveProjectName(root);
            case "purpose" -> root.get("purpose");
            case "company" -> root.get("company");
            case "model" -> root.get("model");
            case "description" -> root.get("description");
            default -> null;
        };

        if (target == null) {
            if ("purchaseDate".equals(field)) {
                return parseDateKeyword(rawKeyword)
                        .map(date -> buildPurchaseDatePredicate(root, cb, date.toString()))
                        .orElse(cb.conjunction());
            }
            return cb.conjunction();
        }
        return likeExpression(target, lowered, cb);
    }

    private static Predicate likeExpression(Expression<String> expression,
                                            String loweredKeyword,
                                            CriteriaBuilder cb) {
        if (expression == null) {
            return null;
        }
        return cb.like(cb.lower(expression), loweredKeyword);
    }

    private static Predicate buildPurchaseDatePredicate(Root<Devices> root,
                                                        CriteriaBuilder cb,
                                                        String value) {
        LocalDate date = parseDateKeyword(value).orElse(null);
        if (date == null) {
            return cb.disjunction();
        }
        Date start = Date.from(date.atStartOfDay(DEFAULT_ZONE_ID).toInstant());
        Date end = Date.from(date.plusDays(1).atStartOfDay(DEFAULT_ZONE_ID).toInstant());
        return cb.and(
                cb.greaterThanOrEqualTo(root.get("purchaseDate"), start),
                cb.lessThan(root.get("purchaseDate"), end)
        );
    }

    private static Optional<LocalDate> parseDateKeyword(String value) {
        if (value == null || value.length() < 8) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Expression<String> resolveCategoryName(Root<Devices> root) {
        return getOrCreateJoin(root, "categoryId").get("name");
    }

    private static Expression<String> resolveManageDepartmentName(Root<Devices> root) {
        return getOrCreateJoin(root, "manageDep").get("name");
    }

    private static Expression<String> resolveProjectName(Root<Devices> root) {
        return getOrCreateJoin(root, "projectId").get("name");
    }

    private static Expression<String> normalizeExpression(Expression<String> expression, CriteriaBuilder cb) {
        if (expression == null) {
            return null;
        }
        return cb.lower(cb.trim(expression));
    }

    private static Expression<String> trimExpression(Expression<String> expression, CriteriaBuilder cb) {
        if (expression == null) {
            return null;
        }
        return cb.trim(expression);
    }

    @SuppressWarnings("unchecked")
    private static <T> Join<Devices, T> getOrCreateJoin(Root<Devices> root, String attribute) {
        for (Join<Devices, ?> join : root.getJoins()) {
            if (join.getAttribute() != null && attribute.equals(join.getAttribute().getName())) {
                return (Join<Devices, T>) join;
            }
        }
        return root.join(attribute, JoinType.LEFT);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record AdminDeviceSearchContext(String filterField,
                                           String filterValue,
                                           String keyword,
                                           boolean disposedOnly) {
    }
}
