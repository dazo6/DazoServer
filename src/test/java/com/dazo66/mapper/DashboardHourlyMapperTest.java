package com.dazo66.mapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.dazo66.dashboard.HourlyArtistCount;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DashboardHourlyMapperTest {
    @Test void groupsByArtistWithExactHourBoundariesAndTheSameTimelineFilter() throws Exception {
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:hourly;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = source.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE t_fanbox_artist(id INT PRIMARY KEY, name VARCHAR(255), icon VARCHAR(255), enable INT)");
            sql.execute("CREATE TABLE t_crawler_request(id INT PRIMARY KEY, artist_id INT, url VARCHAR(1024), gmt_create TIMESTAMP, is_done INT)");
            sql.execute("INSERT INTO t_fanbox_artist VALUES(7,'Disabled Artist','icon.jpg',0)");
            sql.execute("INSERT INTO t_crawler_request VALUES(1,7,'/fanbox/user/42/post/1','2026-09-12 09:00:00',0),"
                    + "(2,7,'/fanbox/user/42/post/2','2026-09-12 09:59:59',1),"
                    + "(3,NULL,'https://api.fanbox.cc/post.info?postId=3','2026-09-12 09:30:00',0),"
                    + "(4,999,'/fanbox/user/999/post/4','2026-09-12 09:30:00',1),"
                    + "(5,7,'/fanbox/user/42?o=50','2026-09-12 09:30:00',0),"
                    + "(6,7,'/fanbox/user/42/post/6','2026-09-12 08:59:59',1),"
                    + "(7,7,'/fanbox/user/42/post/7','2026-09-12 10:00:00',1)");
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.addMapper(DashboardMapper.class);
        try (SqlSession session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession()) {
            List<HourlyArtistCount> rows = session.getMapper(DashboardMapper.class)
                    .selectHourlyArtists("2026-09-12 09:00:00", "2026-09-12 10:00:00");
            assertThat(rows).hasSize(2);
            assertThat(rows).extracting(HourlyArtistCount::getCount).containsOnly(2L);
            HourlyArtistCount known = rows.stream().filter(row -> row.getArtistId() != null).findFirst().get();
            assertThat(known.getArtistId()).isEqualTo(7);
            assertThat(known.getName()).isEqualTo("Disabled Artist");
            assertThat(known.getIcon()).isEqualTo("icon.jpg");
            assertThat(rows.stream().filter(row -> row.getArtistId() == null).count()).isEqualTo(1);
        }
    }
}
