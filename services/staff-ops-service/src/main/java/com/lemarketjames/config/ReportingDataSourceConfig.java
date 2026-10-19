package com.lemarketjames.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The reports' own connection pool, so reports can't starve live trading (LMKT-138). It reaches
 * the same database as the ordinary pool, but every connection it opens is held by PostgreSQL to
 * read-only transactions and a statement timeout ({@code reporting.datasource.hikari.*} in
 * application.properties), and it is small.
 *
 * <p>Every repository under {@code com.lemarketjames.reports} uses it, through its own entity
 * manager; a report that reads through JDBC asks for {@link #JDBC}. Surveillance writes alerts,
 * so it can't share these limits and uses the ordinary pool ({@link PrimaryDataSourceConfig}).
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.lemarketjames.reports",
        entityManagerFactoryRef = ReportingDataSourceConfig.ENTITY_MANAGER_FACTORY,
        transactionManagerRef = ReportingDataSourceConfig.TRANSACTION_MANAGER)
public class ReportingDataSourceConfig {

    /** Bean names, for the {@code @Qualifier}s and {@code @Transactional}s of report code. */
    public static final String DATA_SOURCE = "reportingDataSource";
    public static final String ENTITY_MANAGER_FACTORY = "reportingEntityManagerFactory";
    public static final String TRANSACTION_MANAGER = "reportingTransactionManager";
    public static final String JDBC = "reportingJdbcTemplate";

    /** The database and account of {@code spring.datasource.*}, with the read-side limits on top. */
    @Bean(DATA_SOURCE)
    @ConfigurationProperties("reporting.datasource.hikari")
    public HikariDataSource reportingDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean(ENTITY_MANAGER_FACTORY)
    public LocalContainerEntityManagerFactoryBean reportingEntityManagerFactory(
            EntityManagerFactoryBuilder builder, @Qualifier(DATA_SOURCE) HikariDataSource dataSource) {
        return builder.dataSource(dataSource)
                .packages("com.lemarketjames.reports")
                .persistenceUnit("reporting")
                .build();
    }

    @Bean(TRANSACTION_MANAGER)
    public PlatformTransactionManager reportingTransactionManager(
            @Qualifier(ENTITY_MANAGER_FACTORY) EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean(JDBC)
    public JdbcTemplate reportingJdbcTemplate(@Qualifier(DATA_SOURCE) HikariDataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
