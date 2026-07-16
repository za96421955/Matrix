package com.matrix.service.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.matrix.service.context.TaskContext;
import com.matrix.service.dal.entity.TaskInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 任务表 Mapper 接口
 * <p>
 * 已废弃，任务持久化已迁移至 Redis（由 {@link TaskContext} 管理）。
 * 保留此类以防回滚需要。
 * </p>
 *
 * @deprecated 任务数据已全部迁移至 Redis，不再使用 MySQL。如需回滚可恢复此 Mapper。
 */
@Deprecated
@Mapper
public interface TaskInfoMapper extends BaseMapper<TaskInfo> {

    /**
     * 根据 taskId 查询任务详情
     *
     * @param taskId 任务 ID
     * @return 任务详情
     */
    @Select("SELECT * FROM tbl_task_info WHERE user_id = #{userId} AND task_id = #{taskId} AND is_deleted = 0")
    TaskInfo selectByTaskId(@Param("userId") Long userId, @Param("taskId") String taskId);

}
