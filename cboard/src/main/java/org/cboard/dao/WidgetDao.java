package org.cboard.dao;

import org.apache.ibatis.annotations.Param;
import org.cboard.pojo.DashboardWidget;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Created by yfyuan on 2016/8/122.
 */
@Repository
public interface WidgetDao {

    List<String> getCategoryList();

    List<DashboardWidget> getAllWidgetList();

    List<DashboardWidget> getWidgetList(@Param("0") String userId);

    List<DashboardWidget> getWidgetListAdmin(@Param("0") String userId);

    int save(DashboardWidget dashboardWidget);

    long countExistWidgetName(Map<String, Object> map);

    int update(DashboardWidget dashboardWidget);

    int delete(@Param("0") Long id, @Param("1") String userId);

    DashboardWidget getWidget(@Param("0") Long id);

    long checkWidgetRole(@Param("0") String userId, @Param("1") Long widgetId, @Param("2") String permissionPattern);
}
