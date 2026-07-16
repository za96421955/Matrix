package com.matrix.service.service.app;

import com.matrix.service.service.task.Executor;
import jakarta.annotation.Resource;

/**
 * 用户工具调用抽象
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public abstract class AbstractApplication implements Application {

    @Resource
    protected Executor executor;

}


