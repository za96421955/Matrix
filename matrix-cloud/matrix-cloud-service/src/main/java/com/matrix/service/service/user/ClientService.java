package com.matrix.service.service.user;

import com.matrix.service.dal.entity.ClientInfo;
import com.matrix.common.dto.command.RegisterCommand;

import java.util.List;

/**
 * 终端服务接口
 */
public interface ClientService {

    /**
     * @description 查询用户终端
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    List<ClientInfo> getByUserId(Long userId);

    /**
     * @description ID 查询终端信息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    ClientInfo getById(Long userId, String clientId);

    /**
     * @description 查询在线用户终端
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    List<ClientInfo> getByUserIdAndOnline(Long userId);

    /**
     * @description 添加终端信息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    void save(Long userId, String clientId, String clientType, String osInfo);

    /**
     * 终端注册
     * 
     * @param userId 用户 ID
     * @param clientId 客户端 ID
     */
    void register(Long userId, String clientId, RegisterCommand registerCommand);
    
    /**
     * 处理心跳
     * 
     * @param userId 用户 ID
     * @param clientId 客户端 ID
     */
    void heartbeat(Long userId, String clientId, RegisterCommand registerCommand);

    /**
     * 检查终端是否在线
     *
     * @param clientId 客户端 ID
     * @return 是否在线
     */
    boolean checkOnline(Long userId, String clientId);

}


