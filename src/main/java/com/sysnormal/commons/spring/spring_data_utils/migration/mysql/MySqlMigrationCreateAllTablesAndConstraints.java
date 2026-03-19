package com.sysnormal.commons.spring.spring_data_utils.migration.mysql;

import com.sysnormal.commons.core.utils_core.Constants;
import com.sysnormal.commons.spring.spring_data_utils.MysqlDatabaseUtils;
import com.sysnormal.commons.spring.spring_data_utils.migration.BaseCreationTableStatement;
import jakarta.persistence.Entity;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.PersistentClass;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Set;

public class MySqlMigrationCreateAllTablesAndConstraints {

    private static final Logger logger = LoggerFactory.getLogger(MySqlMigrationCreateAllTablesAndConstraints.class);

    private static MysqlDatabaseUtils mysqlDatabaseUtils = new MysqlDatabaseUtils();

    public static void migrate(Connection conn, String[] packagesToScan) throws SQLException {
        logger.debug("INIT {}.{}",MySqlMigrationCreateAllTablesAndConstraints.class.getSimpleName(), "migrate");
        try {

            String[] basePackages = packagesToScan;

            //pre-registry (flyway run before spring hibernate loaded) hibernate to get metadata
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySetting(Constants.Hibernate.DIALECT.PROPERTY, "org.hibernate.dialect.MySQLDialect")
                    .applySetting(org.hibernate.cfg.AvailableSettings.HBM2DDL_AUTO, Constants.Hibernate.HBM2DDL.AUTO.VALUES.NONE)
                    .applySetting(Constants.Hibernate.CONNECTION.PROVIDER_DISABLES_AUTOCOMMIT.PROPERTY, true)
                    .applySetting(Constants.Hibernate.BOOT.ALLOW_JDBC_METADATA_ACCESS.PROPERTY, false)
                    .build();
            MetadataSources metadataSources = new MetadataSources(registry);
            ArrayList<MySqlCreationTableStatement> creationStatements = new ArrayList<>();

            for(String basePackage : basePackages) {

                //get all classes wich is entity
                //Reflections reflections = new Reflections(basePackage, Scanners.TypesAnnotated);
                Reflections reflections = new Reflections(new ConfigurationBuilder()
                        .setUrls(ClasspathHelper.forPackage(basePackage))
                        .setScanners(Scanners.TypesAnnotated)
                        .filterInputsBy(new FilterBuilder().includePackage(basePackage))
                );
                Set<Class<?>> entities = reflections.getTypesAnnotatedWith(Entity.class);
                logger.info("Reflections found {} entities in package {}", entities.size(), basePackage);

                //add all reflected entities to metadatasource
                for (Class<?> ent : entities) {
                    logger.debug("  -> adding entity: {}, {}", ent.getName(), ent.getDeclaredFields().length);
                    metadataSources.addAnnotatedClass(ent);
                }
            }

            //build metadata
            Metadata metadata = metadataSources.buildMetadata();
            logger.info("Hibernate metadata contains {} entity bindings on migrate", metadata.getEntityBindings().size());


            //iterate over all entities of metadata
            for (PersistentClass persistentClass : metadata.getEntityBindings()) {
                creationStatements.add(new MySqlCreationTableStatement(persistentClass, metadata));
            }

                //create all tabels
            for (MySqlCreationTableStatement creationStatement : creationStatements) {
                try {
                    try (Statement stmt = conn.createStatement()) {
                        logger.debug("{}", creationStatement.getCreateTable());
                        stmt.execute(creationStatement.getCreateTable());
                    }
                } catch (SQLException e) {
                    logger.error(e.getMessage());
                    logger.debug(e.getMessage());
                    e.printStackTrace();
                }
            }


            //create all unique constraints
            for (MySqlCreationTableStatement creationStatement : creationStatements) {
                for (BaseCreationTableStatement.Constraint uniqueConstraintsStatement : creationStatement.getAddUniqueConstraints()) {
                    if (!mysqlDatabaseUtils.indexExists(conn, uniqueConstraintsStatement.getTableName(),uniqueConstraintsStatement.getName())) {
                        try {
                            try (Statement stmt = conn.createStatement()) {
                                logger.debug("{}", uniqueConstraintsStatement.getCreationQuery());
                                stmt.execute(uniqueConstraintsStatement.getCreationQuery());
                            }
                        } catch (SQLException e) {
                            logger.error(e.getMessage());
                            logger.debug(e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        logger.debug("on table {} unique key {} already exists",uniqueConstraintsStatement.getTableName(), uniqueConstraintsStatement.getTableName());
                    }
                }
            }


            //create all foreign keys
            for (MySqlCreationTableStatement creationStatement : creationStatements) {
                for (BaseCreationTableStatement.ForeignKey addForeignKeyConstraintsStatement : creationStatement.getAddForeignKeys()) {
                    if (!mysqlDatabaseUtils.foreignKeyExists(conn, addForeignKeyConstraintsStatement.getTableName(),addForeignKeyConstraintsStatement.getName())) {
                        try {
                            try (Statement stmt = conn.createStatement()) {
                                logger.debug("{}", addForeignKeyConstraintsStatement.getCreationQuery());
                                stmt.execute(addForeignKeyConstraintsStatement.getCreationQuery());
                            }
                        } catch (SQLException e) {
                            logger.error(e.getMessage());
                            logger.debug(e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        logger.debug("on table {} foreign key {} already exists",addForeignKeyConstraintsStatement.getTableName(), addForeignKeyConstraintsStatement.getTableName());
                    }
                }
            }

            //create all indexes
            for (MySqlCreationTableStatement creationStatement : creationStatements) {
                for (BaseCreationTableStatement.Index addIndex : creationStatement.getAddIndexes()) {
                    if (!mysqlDatabaseUtils.indexExists(conn, addIndex.getTableName(),addIndex.getName())) {
                        try {
                            try (Statement stmt = conn.createStatement()) {
                                logger.debug("{}", addIndex.getCreationQuery());
                                stmt.execute(addIndex.getCreationQuery());
                            }
                        } catch (SQLException e) {
                            logger.error(e.getMessage());
                            logger.debug(e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        logger.debug("on table {} index {} already exists",addIndex.getTableName(), addIndex.getTableName());
                    }
                }
            }

        } catch (Exception e) {
            logger.debug(e.getMessage());
            logger.debug(e.getLocalizedMessage());
            logger.error(e.getMessage());
            logger.error(e.getLocalizedMessage());
            e.printStackTrace();
        }
        logger.debug("END {}.{}",MySqlMigrationCreateAllTablesAndConstraints.class.getSimpleName(), "migrate");
    }
}
