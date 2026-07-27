package com.matrix.service.context;

import com.matrix.service.service.app.Application;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AppContext {

    @Resource
    private ApplicationContext applicationContext;

    /** 获取Apps属性值 */
    public List<Application> getApps() {
        return new ArrayList<>(applicationContext.getBeansOfType(Application.class).values());
    }

    /** 获取App属性值 */
    public Application getApp(String fileType) {
        for (Application application : this.getApps()) {
            if (application.fileType().equals(fileType)) {
                return application;
            }
        }
        return null;
    }

}


