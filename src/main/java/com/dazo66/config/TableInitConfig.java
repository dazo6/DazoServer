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
        ensureArtistIconColumn(sqlSession);
        ensureArtistBannerColumn(sqlSession);
        com.dazo66.util.CrawlerRequestArtistSchema.ensure(sqlSession.getConnection());
        com.dazo66.util.CrawlerRequestArtistSchema.ensureTimelineIndex(sqlSession.getConnection());
        sqlSession.commit();
        sqlSession.close();
    }

    private void ensureArtistIconColumn(SqlSession sqlSession) throws SQLException {
        Statement statement = sqlSession.getConnection().createStatement();
        ResultSet columns = statement.executeQuery("SHOW COLUMNS FROM t_fanbox_artist LIKE 'icon'");
        if (!columns.next()) {
            statement.execute("ALTER TABLE t_fanbox_artist ADD COLUMN icon varchar(1024) NULL AFTER name");
            log.info("add icon column to t_fanbox_artist");
        }
        columns.close();
        statement.close();
    }

    private void ensureArtistBannerColumn(SqlSession sqlSession) throws SQLException {
        Statement statement = sqlSession.getConnection().createStatement();
        ResultSet columns = statement.executeQuery("SHOW COLUMNS FROM t_fanbox_artist LIKE 'banner'");
        if (!columns.next()) {
            statement.execute("ALTER TABLE t_fanbox_artist ADD COLUMN banner varchar(1024) NULL AFTER icon");
            log.info("add banner column to t_fanbox_artist");
        }
        columns.close();
        statement.close();
    }
}
