package org.cboard.dao;

import org.apache.ibatis.annotations.Param;
import org.cboard.pojo.DashboardUser;
import org.cboard.pojo.DashboardUserRole;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Created by yfyuan on 2016/12/2.
 */
@Repository
public interface UserDao {
    int save(DashboardUser user);

    int deleteUserById(@Param("0") String userId);

    List<DashboardUser> getUserList();

    int update(DashboardUser user);

    int saveUserRole(List<DashboardUserRole> list);

    int deleteUserRole(Map<String, Object> param);

    List<DashboardUserRole> getUserRoleList();

    DashboardUser getUserByLoginName(@Param("0") String loginName);

    int saveNewUser(@Param("0") String userId, @Param("1") String user_name, @Param("2") String loginName);

    int updateUserPassword(@Param("0") String userId, @Param("1") String passowrd, @Param("3") String newPassword);

    int deleteUserRoleByRoleId(@Param("0") String roleId);

    int deleteUserRoles(Map<String, Object> param);

}
