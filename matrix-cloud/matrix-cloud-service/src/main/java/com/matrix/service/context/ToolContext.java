package com.matrix.service.context;

import com.matrix.service.service.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ToolContext {

    @Resource
    private ApplicationContext applicationContext;

    /** 获取Tools属性值 */
    public List<Tool> getTools() {
        return new ArrayList<>(applicationContext.getBeansOfType(Tool.class).values());
    }

    /** 获取Tool属性值 */
    public Tool<?> getTool(String name) {
        for (Tool<?> tool : this.getTools()) {
            if (tool.name().equals(name)) {
                return tool;
            }
        }
        return null;
    }

}


