package org.cboard.dao;

import org.apache.ibatis.annotations.Param;
import org.cboard.pojo.DashboardDataset;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Created by yfyuan on 2016/10/11.
 */
@Repository
public interface DatasetDao {

    List<String> getCategoryList();

    List<DashboardDataset> getAllDatasetList();

    List<DashboardDataset> getDatasetList(@Param("0") String userId);

    List<DashboardDataset> getDatasetListAdmin(@Param("0") String userId);

    int save(DashboardDataset dataset);

    long countExistDatasetName(Map<String, Object> map);

    int update(DashboardDataset dataset);

    int delete(@Param("0") Long id, @Param("1") String userId);

    DashboardDataset getDataset(@Param("0") Long id);

    long checkDatasetRole(@Param("0") String userId, @Param("1") Long widgetId, @Param("2") String permissionPattern);

}
