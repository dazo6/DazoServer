package org.cboard.dao;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Created by yfyuan on 2016/12/21.
 */
@Repository
public interface MenuDao {
    List<Long> getMenuIdByUserRole(@Param("0") String userId);

    List<Long> getMenuIdByRoleAdmin(@Param("0") String userId);
}
