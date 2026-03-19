package com.sysnormal.commons.spring.spring_data_utils.migration.mysql;

import com.sysnormal.commons.spring.spring_data_utils.JpaReflectionUtils;
import com.sysnormal.commons.spring.spring_data_utils.MysqlDatabaseUtils;
import com.sysnormal.commons.spring.spring_data_utils.migration.BaseCreationTableStatement;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import org.hibernate.annotations.*;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.mapping.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * implementation of base creation table statement for mysql database
 *
 * @author Alencar
 * @version 1.0.0
 */
public class MySqlCreationTableStatement extends BaseCreationTableStatement {

    private static final Logger logger = LoggerFactory.getLogger(MySqlCreationTableStatement.class);

    public MySqlCreationTableStatement(PersistentClass persistentEntity, Metadata metadata) {
        super(persistentEntity, metadata);
    }


    @Override
    public void buildCreateTableStatement() {
        logger.debug("INIT {}.{}",this.getClass().getSimpleName(), "buildCreateTableStatement");
        try {
            createTable = "create table if not exists `" + tableName + "` (";
            ArrayList<String> columnsStmt = new ArrayList<String>();
            for (int i = 0; i < clazzFields.size(); i++) {
                Field field = clazzFields.get(i);
                jakarta.persistence.Column columnAnotation = field.getAnnotation(jakarta.persistence.Column.class);
                if (columnAnotation != null) {
                    String columnStatement = null;

                    //sql column name
                    String columnName = columnAnotation.name();
                    if (columnName == null) columnName = field.getName();
                    columnName = "`" + columnName + "`";
                    columnStatement = columnName;

                    Column tabColumn = table.getColumn(Identifier.toIdentifier(columnName));

                    //sql column definition
                    String columnDefinition = "";
                    if (StringUtils.hasText(columnAnotation.columnDefinition())) {
                        columnDefinition = columnAnotation.columnDefinition();
                        if (columnDefinition.toLowerCase().trim().indexOf(tabColumn.getName().toLowerCase().trim()) == 0 || columnDefinition.toLowerCase().indexOf(tabColumn.getName().toLowerCase().trim()) == 1) {
                            columnStatement = columnDefinition;
                        }
                    }

                    //sql column type definition
                    String sqlType = tabColumn.getSqlType(metadata);
                    columnStatement = checkColumnStatmentContainsOrAdd(columnStatement, columnDefinition, sqlType);

                    //sql not null constraint
                    if (!(columnAnotation.nullable() || tabColumn.isNullable())) {
                        columnStatement = checkColumnStatmentContainsOrAdd(columnStatement, columnDefinition, "not null");
                    }

                    GeneratedValue generatedValue = field.getAnnotation(GeneratedValue.class);
                    if (generatedValue != null) {
                        if (generatedValue.strategy().compareTo(GenerationType.IDENTITY) == 0) {
                            columnStatement = checkColumnStatmentContainsOrAdd(columnStatement, columnDefinition, "auto_increment");
                        }
                    }

                    //sql default value
                    String columnDefault = "";
                    if (StringUtils.hasText(tabColumn.getDefaultValue())) {
                        columnDefault = "default " + tabColumn.getDefaultValue();
                    } else if (field.getAnnotation(ColumnDefault.class) != null) {
                        ColumnDefault columnDefaultAnnotation = field.getAnnotation(ColumnDefault.class);
                        columnDefault = "default " + columnDefaultAnnotation.value();
                    } else if (field.getAnnotation(CreationTimestamp.class) != null) {
                        columnDefault = "default current_timestamp(6)";
                    }
                    if (StringUtils.hasText(columnDefault)) {
                        columnStatement = checkColumnStatmentContainsOrAdd(columnStatement, columnDefinition, columnDefault);
                    }

                    if (field.getAnnotation(UpdateTimestamp.class) != null) {
                        columnStatement = checkColumnStatmentContainsOrAdd(columnStatement, columnDefault, "on update current_timestamp(6)");
                    }

                    //sql check constraint
                    if (!tabColumn.getCheckConstraints().isEmpty() || field.getAnnotation(Check.class) != null) {
                        String checkConstraintStatement = "";
                        ArrayList<String> checkConstraintsStatements = new ArrayList<>();
                        List<CheckConstraint> checkConstraints = tabColumn.getCheckConstraints();
                        if (!checkConstraints.isEmpty()) {
                            for (CheckConstraint checkConstraint : checkConstraints) {
                                checkConstraintsStatements.add(checkConstraint.getConstraint());
                            }
                            checkConstraintStatement = "check (" + String.join(" and ", checkConstraintsStatements) + ")";
                        } else {
                            Check checkConstraint = field.getAnnotation(Check.class);
                            checkConstraintStatement = "check (" + checkConstraint.constraints() + ")";
                        }
                        columnStatement = checkColumnStatmentContainsOrAdd(columnStatement, columnDefinition, checkConstraintStatement);
                    }

                    columnsStmt.add(columnStatement);
                }
            }
            createTable += String.join(",", columnsStmt);

            //primary key fields
            PrimaryKey pk = table.getPrimaryKey();
            if (pk != null) {
                ArrayList<String> primaryKeyFields = new ArrayList<>();
                for (Iterator<?> it = pk.getColumns().iterator(); it.hasNext(); ) {
                    Column col = (Column) it.next();
                    primaryKeyFields.add("`" + col.getName() + "`");
                }
                createTable += ", primary key (" + String.join(",", primaryKeyFields) + ")";
            }
            createTable += ") engine = InnoDB";
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.debug("END {}.{}",this.getClass().getSimpleName(), "buildCreateTableStatement");
    }


    @Override
    public void buildAddUniqueConstraintsStatement() {
        logger.debug("INIT {}.{}",this.getClass().getSimpleName(), "buildAddUniqueConstraintsStatement");
        try {
            Map<String, UniqueKey> uniqueKeys = table.getUniqueKeys();
            addUniqueConstraints = new ArrayList<>();
            if (uniqueKeys.size() > 0) {
                for (Map.Entry<String, UniqueKey> entry : uniqueKeys.entrySet()) {
                    String query = """
                            alter table `""" + tableName + "` " + """
                            add constraint `""" + entry.getKey() + "` unique (" + """
                            """ + entry.getValue().getColumns().stream()
                                .map(Column::getText)
                                .collect(Collectors.joining(","))
                            + """
                            )
                            """;
                    addUniqueConstraints.add(new Constraint(tableName, entry.getKey(), query));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.debug("END {}.{}",this.getClass().getSimpleName(), "buildAddUniqueConstraintsStatement");
    }

    @Override
    public void buildAddForeignKeyConstraintsStatement() {
        logger.debug("INIT {}.{}",this.getClass().getSimpleName(), "buildAddForeignKeyConstraintsStatement");
        try {
            addForeignKeys = new ArrayList<>();
            for (int i = 0; i < clazzFields.size(); i++) {
                Field field = clazzFields.get(i);

                JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
                if (joinColumn != null) {
                    Optional<Class<?>> entityRefType = JpaReflectionUtils.resolveEntityType(field, clazz);
                    if (entityRefType.isPresent()) {
                        String foreigntableName = JpaReflectionUtils.resolveTableName(entityRefType.get());
                        String foreignColumnName = "id";
                        String foreignKeyName = tableName + "_" + joinColumn.name() + "_" + foreigntableName+"_"+foreignColumnName+"_fk";

                        if (foreignKeyName.length() > MysqlDatabaseUtils.MAX_FOREIGN_KEY_NAME_LENGTH) {
                            foreignKeyName = foreignKeyName.replace("_","");
                            if (foreignKeyName.length() > MysqlDatabaseUtils.MAX_FOREIGN_KEY_NAME_LENGTH) {
                                foreignKeyName = foreignKeyName.substring(0,MysqlDatabaseUtils.MAX_FOREIGN_KEY_NAME_LENGTH-3) + "_fk";
                            }
                        }

                        String query =  "alter table `" + tableName + "` add constraint `" + foreignKeyName + "` foreign key (`" + joinColumn.name() + "`) references `" +foreigntableName + "` (`" + foreignColumnName + "`)";
                        OnDelete onDelete = field.getAnnotation(OnDelete.class);
                        if (onDelete != null) {
                            query += " on delete " + onDelete.action().toSqlString();
                        }
                        addForeignKeys.add(new ForeignKey(tableName, foreignKeyName, query));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.debug("END {}.{}",this.getClass().getSimpleName(), "buildAddForeignKeyConstraintsStatement");
    }


    @Override
    public void buildAddIndexesStatement() {
        logger.debug("INIT {}.{}",this.getClass().getSimpleName(), "buildAddIndexesStatement");
        try {
            Map<String, org.hibernate.mapping.Index> indexes = table.getIndexes();
            addIndexes = new ArrayList<>();
            if (indexes.size() > 0) {
                for (Map.Entry<String, org.hibernate.mapping.Index> entry : indexes.entrySet()) {
                    String query = """
                            create index `""" + entry.getValue().getName() + "` on `"+tableName+"`(" + """
                            """ + entry.getValue().getColumns().stream()
                            .map(Column::getText)
                            .collect(Collectors.joining(","))
                            + """
                            )
                            """;
                    addIndexes.add(new Index(tableName,entry.getValue().getName(),query));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.debug("END {}.{}",this.getClass().getSimpleName(), "buildAddIndexesStatement");
    }



}
