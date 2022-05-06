package com.dazo66.config;

import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * @author dazo66
 **/
@Configuration
public class DatasourceConfiguration {

    @Autowired
    private DataSourceProperties dataSourceProperties;

    @Bean
    @Primary
    public DataSource getDatasource() {
        final BasicDataSource basicDataSource = new BasicDataSource();
        basicDataSource.setUrl(dataSourceProperties.getUrl());
        basicDataSource.setDriverClassName(dataSourceProperties.getDriverClassName());
        basicDataSource.setUsername(dataSourceProperties.getUsername());
        basicDataSource.setPassword(dataSourceProperties.getPassword());
        return basicDataSource;
    }


}
