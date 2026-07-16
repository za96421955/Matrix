package com.matrix.client.service;

import java.io.IOException;

/**
 * 系统指纹
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public interface Fingerprint {

    /**
     * @description 获取系统指纹
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    String get() throws IOException, InterruptedException;

}


