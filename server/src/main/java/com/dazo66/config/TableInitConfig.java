package com.dazo66.config;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dazo66.entity.CreateTableSql;
import com.dazo66.util.ClassScanUtils;
import com.google.common.base.Joiner;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * @author Dazo66
 */
@Configuration
@Slf4j
public class TableInitConfig {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @PostConstruct
    private void init() throws SQLException {
        SqlSession sqlSession = sqlSessionFactory.openSession();
        Set<Class> class4Annotation = ClassScanUtils.getClass4Annotation("com.dazo66",
                CreateTableSql.class);
        for (Class c : class4Annotation) {
            TableName tableName = (TableName) c.getDeclaredAnnotation(TableName.class);
            if (tableName != null) {
                String value = tableName.value();
                Statement statement = sqlSession.getConnection().createStatement();
                ResultSet resultSet = statement.executeQuery("SHOW TABLES LIKE '" + value + "'");
                if (!resultSet.next()) {
                    CreateTableSql createTableSqlAnn =
                            (CreateTableSql) c.getDeclaredAnnotation(CreateTableSql.class);
                    String[] values = createTableSqlAnn.value();
                    statement.execute(Joiner.on("\n").join(values));
                    log.info("create table {}", value);
                }
                statement.close();
            }
        }
        sqlSession.commit();
        sqlSession.close();
    }
}
