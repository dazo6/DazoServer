package org.cboard.dao;

import org.apache.ibatis.annotations.Param;
import org.cboard.pojo.DashboardDatasource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Created by yfyuan on 2016/8/15.
 */
@Repository
public interface DatasourceDao {

    String getDatasourceConfig(String userId, String name);

    List<DashboardDatasource> getDatasourceList(String userId);

    List<DashboardDatasource> getDatasourceListAdmin(String userId);

    DashboardDatasource getDatasource(Long datasourceId);

    int save(DashboardDatasource dashboardDatasource);

    long countExistDatasourceName(Map<String, Object> map);

    int update(DashboardDatasource dashboardDatasource);

    int delete(Long id, String userId);

    long checkDatasourceRole(@Param("0") String userId, @Param("1") Long datasourceId, @Param("2") String permissionPattern);
}
