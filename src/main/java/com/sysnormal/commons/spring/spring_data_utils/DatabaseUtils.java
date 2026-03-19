package com.sysnormal.commons.spring.spring_data_utils;


import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class DatabaseUtils {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseUtils.class);
    public abstract boolean indexExists(Connection conn, String tableName, String constraintName) throws SQLException;

    public abstract boolean foreignKeyExists(Connection conn,String tableName,String fkName) throws SQLException;

    public static String detectQueryType(String query) {
        String result = "select";
        if (StringUtils.hasText(query)) {
            String normalized = query.trim().toLowerCase();
            if (normalized.indexOf("insert") == 0) {
                result = "insert";
            } else if (normalized.indexOf("update") == 0) {
                result = "update";
            } else if (normalized.indexOf("delete") == 0) {
                result = "delete";
            }
        }
        return result;
    }


    public static <E> Specification<E> fromWhere(JsonNode where) {
        return (root, query, cb) -> buildPredicate(where, root, cb);
    }

    private static Predicate buildPredicate(
            JsonNode node,
            Root<?> root,
            CriteriaBuilder cb
    ) {
        logger.debug("INIT {}.{}",DatabaseUtils.class.getSimpleName(),"buildPredicate");
        List<Predicate> predicates = new ArrayList<>();
        if (!node.isObject()) {
            throw new IllegalArgumentException("Where clause must be a JSON object");
        }

        node.propertyNames().forEach(key -> {
            JsonNode value = node.get(key);
            logger.debug("xxxxxxxxxxx1 {}",key, value);
            switch (key) {
                case "$and", "and", "&" -> {
                    if (value.isArray() && !value.isEmpty()) {
                        predicates.add(cb.and(parseLogicalArray(value, root, cb)));
                    }
                }
                case "$or", "or", "|", "||" -> {
                    if (value.isArray() && !value.isEmpty()) {
                        predicates.add(cb.or(parseLogicalArray(value, root, cb)));
                    }
                }

                default -> predicates.add(parseFieldPredicate(key, value, root, cb));
            }
        });
        logger.debug("END {}.{}",DatabaseUtils.class.getSimpleName(),"buildPredicate");
        return cb.and(predicates.toArray(new Predicate[0]));
    }


    private static Predicate[] parseLogicalArray(
            JsonNode arrayNode,
            Root<?> root,
            CriteriaBuilder cb
    ) {
        logger.debug("INIT {}.{}",DatabaseUtils.class.getSimpleName(),"parseLogicalArray");
        List<Predicate> predicates = new ArrayList<>();

        arrayNode.forEach(n ->
                predicates.add(buildPredicate(n, root, cb))
        );
        logger.debug("END {}.{}",DatabaseUtils.class.getSimpleName(),"parseLogicalArray");
        return predicates.toArray(new Predicate[0]);
    }

    private static String normalizeLikeValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "%";
        }

        boolean startsWithPercent = raw.startsWith("%");
        boolean endsWithPercent = raw.endsWith("%");

        String core = raw;

        // remove % externos para tratar o conteúdo interno
        if (startsWithPercent) core = core.substring(1);
        if (endsWithPercent && core.length() > 0) core = core.substring(0, core.length() - 1);

        // escapa curingas internos
        core = core
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");

        StringBuilder result = new StringBuilder();

        if (startsWithPercent) result.append("%");
        else result.append("%");

        result.append(core);

        if (endsWithPercent) result.append("%");
        else result.append("%");

        return result.toString();
    }


    private static Predicate parseFieldPredicate(
            String field,
            JsonNode operations,
            Root<?> root,
            CriteriaBuilder cb
    ) {
        logger.debug("INIT {}.{}", DatabaseUtils.class.getSimpleName(), "parseFieldPredicate");

        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Field name cannot be null or blank");
        }

        Path<?> path = root.get(field);

        // =========================
        // CASO 1: valor direto → eq implícito
        // =========================
        if (operations == null || !operations.isObject()) {
            Object objectValue = parseValue(path, operations);

            logger.debug("Implicit EQ for field '{}' with value '{}'", field, objectValue);

            Predicate result = (objectValue == null)
                    ? cb.isNull(path)
                    : cb.equal(path, objectValue);

            logger.debug("END {}.{}", DatabaseUtils.class.getSimpleName(), "parseFieldPredicate");
            return result;
        }

        // =========================
        // CASO 2: objeto com operadores
        // =========================
        List<Predicate> predicates = new ArrayList<>();

        operations.propertyNames().forEach(op -> {

            JsonNode valueNode = operations.get(op);
            Object objectValue = parseValue(path, valueNode);

            logger.debug("Operator '{}' on field '{}' with parsed value '{}'", op, field, objectValue);

            Predicate predicate = switch (op) {

                // =========================
                // EQ
                // =========================
                case "$eq", "eq", "=" ->
                        (objectValue == null)
                                ? cb.isNull(path)
                                : cb.equal(path, objectValue);

                // =========================
                // NE
                // =========================
                case "$ne", "ne", "<>", "!=" ->
                        (objectValue == null)
                                ? cb.isNotNull(path)
                                : cb.notEqual(path, objectValue);

                // =========================
                // LIKE
                // =========================
                case "$like", "like" -> {
                    if (objectValue == null) {
                        throw new IllegalArgumentException("LIKE operator does not accept null for field: " + field);
                    }

                    String normalized = normalizeLikeValue(valueNode.asText());
                    yield cb.like(path.as(String.class), normalized, '\\');
                }

                // =========================
                // GT
                // =========================
                case "$gt", "gt", ">" -> {
                    if (objectValue == null) {
                        throw new IllegalArgumentException("GT operator does not accept null for field: " + field);
                    }
                    yield cb.greaterThan(
                            path.as(Comparable.class),
                            (Comparable) objectValue
                    );
                }

                // =========================
                // GTE
                // =========================
                case "$gte", "gte", ">=" -> {
                    if (objectValue == null) {
                        throw new IllegalArgumentException("GTE operator does not accept null for field: " + field);
                    }
                    yield cb.greaterThanOrEqualTo(
                            path.as(Comparable.class),
                            (Comparable) objectValue
                    );
                }

                // =========================
                // LT
                // =========================
                case "$lt", "lt", "<" -> {
                    if (objectValue == null) {
                        throw new IllegalArgumentException("LT operator does not accept null for field: " + field);
                    }
                    yield cb.lessThan(
                            path.as(Comparable.class),
                            (Comparable) objectValue
                    );
                }

                // =========================
                // LTE
                // =========================
                case "$lte", "lte", "<=" -> {
                    if (objectValue == null) {
                        throw new IllegalArgumentException("LTE operator does not accept null for field: " + field);
                    }
                    yield cb.lessThanOrEqualTo(
                            path.as(Comparable.class),
                            (Comparable) objectValue
                    );
                }

                // =========================
                // IN (tratamento correto de NULL)
                // =========================
                case "$in", "in" -> {

                    if (valueNode == null || !valueNode.isArray()) {
                        throw new IllegalArgumentException("IN operator requires an array for field: " + field);
                    }

                    CriteriaBuilder.In<Object> in = cb.in(path);
                    boolean containsNull = false;
                    boolean containsNonNull = false;

                    for (JsonNode element : valueNode) {
                        Object parsed = parseValue(path, element);
                        if (parsed == null) {
                            containsNull = true;
                        } else {
                            containsNonNull = true;
                            in.value(parsed);
                        }
                    }

                    if (!containsNonNull && containsNull) {
                        // apenas null no array
                        yield cb.isNull(path);
                    }

                    if (containsNonNull && containsNull) {
                        // mistura valores + null
                        yield cb.or(in, cb.isNull(path));
                    }

                    yield in;
                }

                default -> throw new IllegalArgumentException(
                        "Unsupported operator: " + op + " for field: " + field
                );
            };

            predicates.add(predicate);
        });

        Predicate result = cb.and(predicates.toArray(new Predicate[0]));

        logger.debug("END {}.{}", DatabaseUtils.class.getSimpleName(), "parseFieldPredicate");

        return result;
    }


    private static Object parseValue(Path<?> path, JsonNode value) {

        Class<?> javaType = path.getJavaType();

        logger.debug(
                "Parsing value '{}' | javaType={} | isNullNode={} | isMissingNode={} | isArray={} | isObject={}",
                value,
                javaType != null ? javaType.getName() : null,
                value != null && value.isNull(),
                value != null && value.isMissingNode(),
                value != null && value.isArray(),
                value != null && value.isObject()
        );

        // =========================
        // 1 Null handling
        // =========================
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }

        // =========================
        // 2 Bloqueio de estruturas inválidas
        // =========================
        if (value.isArray() || value.isObject()) {
            throw new IllegalArgumentException(
                    "Invalid scalar value for field of type "
                            + javaType.getSimpleName()
                            + ". Arrays/Objects are not allowed here: "
                            + value
            );
        }

        // =========================
        // 3 Tipos numéricos
        // =========================
        if (Long.class.equals(javaType) || long.class.equals(javaType)) {
            return value.asLong();
        }

        if (Integer.class.equals(javaType) || int.class.equals(javaType)) {
            return value.asInt();
        }

        if (Double.class.equals(javaType) || double.class.equals(javaType)) {
            return value.asDouble();
        }

        if (Float.class.equals(javaType) || float.class.equals(javaType)) {
            return (float) value.asDouble();
        }

        if (Number.class.equals(javaType)) {
            return value.numberValue();
        }

        // =========================
        // 4 Boolean
        // =========================
        if (Boolean.class.equals(javaType) || boolean.class.equals(javaType)) {
            return value.asBoolean();
        }

        // =========================
        // 5 String
        // =========================
        if (String.class.equals(javaType)) {
            return value.asText();
        }

        // =========================
        // 6 num
        // =========================
        if (javaType.isEnum()) {
            String text = value.asText();
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object enumValue = Enum.valueOf((Class<Enum>) javaType, text);
                return enumValue;
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid enum value '" + text + "' for type " + javaType.getSimpleName(),
                        e
                );
            }
        }

        // =========================
        // 7 Fallback controlado
        // =========================
        if (value.isNumber()) {
            return value.numberValue();
        }

        if (value.isBoolean()) {
            return value.asBoolean();
        }

        if (value.isTextual()) {
            return value.asText();
        }

        // =========================
        // 8 Caso extremo (não deveria ocorrer)
        // =========================
        throw new IllegalArgumentException(
                "Unsupported value type for field of type "
                        + javaType.getSimpleName()
                        + ": "
                        + value
        );
    }

}