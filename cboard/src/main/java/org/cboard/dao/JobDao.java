package org.cboard.dao;

import org.apache.ibatis.annotations.Param;
import org.cboard.pojo.DashboardJob;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * Created by yfyuan on 2017/2/17.
 */
@Repository
public interface JobDao {
    int save(DashboardJob job);

    int update(DashboardJob job);

    List<DashboardJob> getJobList(@Param("0") String userId);

    List<DashboardJob> getJobListAdmin(@Param("0") String userId);

    int delete(@Param("0") Long jobId);

    int updateLastExecTime(@Param("0") Long jobId, @Param("1") Date date);

    int updateStatus(@Param("0") Long jobId, @Param("1") Long status, @Param("2") String log);

    DashboardJob getJob(@Param("0") Long jobId);

    long checkJobRole(@Param("0") String userId, @Param("1") Long jobId, @Param("2") String permissionPattern);

}
