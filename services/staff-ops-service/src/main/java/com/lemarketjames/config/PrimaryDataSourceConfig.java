package com.lemarketjames.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * The ordinary, writable connection pool, for everything but reports: the surveillance alerts the
 * order events raise, and the shared account entities the JWT filter checks a session against.
 *
 * <p>Spelled out because this service has a second pool ({@link ReportingDataSourceConfig}), and
 * Spring Boot builds its single default pool, entity manager and transaction manager only when
 * nobody declares one. Each bean here is the {@code @Primary} one, so code that names no pool
 * gets this one.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = {"com.lemarketjames.common", "com.lemarketjames.surveillance"},
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager")
public class PrimaryDataSourceConfig {

    /** Uses {@code spring.datasource.*} as Spring Boot's own pool would, Hikari settings included. */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder builder,
                                                                       DataSource dataSource) {
        return builder.dataSource(dataSource)
                .packages("com.lemarketjames.common", "com.lemarketjames.surveillance")
                .persistenceUnit("default")
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    /** For libs/common's start-up check of the audit lockdown, which reads the account's rights. */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
