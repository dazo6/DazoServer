package org.cboard.dao;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * Created by february on 2018/12/20.
 */
@Repository
public interface HomepageDao {
    
    int saveHomepage(@Param("0") Long boardId, @Param("0") String userId);
    
    int resetHomepage(@Param("0") String userId);
    
    Long selectHomepage(@Param("0") String userId);
}
