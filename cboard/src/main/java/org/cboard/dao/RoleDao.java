package org.cboard.dao;

import org.apache.ibatis.annotations.Param;
import org.cboard.pojo.DashboardRole;
import org.cboard.pojo.DashboardRoleRes;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Created by yfyuan on 2016/12/6.
 */
@Repository
public interface RoleDao {
    int save(DashboardRole role);

    List<DashboardRole> getRoleList(@Param("0") String userId);

    List<DashboardRole> getCurrentRoleList(@Param("0") String userId);

    List<DashboardRole> getRoleListAll();

    int update(DashboardRole role);

    List<DashboardRoleRes> getRoleResList();

    int saveRoleRes(DashboardRoleRes item);

    int deleteRoleRes(@Param("0") String roleId);

    int deleteRoleResByResId(@Param("0") Long resId, @Param("1") String resType);

    List<Long> getRoleResByResIds(@Param("0") String userId, @Param("1") String resType);

    DashboardRole getRole(@Param("0") String roleId);

    int deleteRole(@Param("0") String roleId);

    List<DashboardRoleRes> getUserRoleResList(@Param("0") String userId, @Param("1") String resType);
}
