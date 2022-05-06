package org.cboard.dao;

import org.apache.ibatis.annotations.Param;
import org.cboard.pojo.DashboardBoard;
import org.cboard.pojo.DashboardBoardParam;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Created by yfyuan on 2016/8/23.
 */
@Repository
public interface BoardDao {

    int save(DashboardBoard board);

    List<DashboardBoard> getBoardList(@Param("0") String userId);

    List<DashboardBoard> getBoardListAdmin(@Param("0") String userId);

    long countExistBoardName(Map<String, Object> map);

    int update(DashboardBoard board);

    int delete(@Param("0") Long id, @Param("1") String userId);

    DashboardBoard getBoard(@Param("0") Long id);

    long checkBoardRole(@Param("0") String userId, @Param("1") Long boardId, @Param("2") String permissionPattern);

    DashboardBoardParam getBoardParam(@Param("0") Long boardId, @Param("1") String userId);

    int saveBoardParam(DashboardBoardParam boardParam);

    int deleteBoardParam(@Param("0") Long boardId, @Param("1") String userId);
}
