package com.matrix.service.service.agent.draco;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @description DRACO 对象
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Evaluation implements Serializable {
    @Serial
    private static final long serialVersionUID = 2568188717250430073L;

    private String id;
    private String problem;
    private Answer answer;

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Answer {
        private String id;
        private List<Section> sections;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        private String id;
        private String title;
        private List<Criterion> criteria;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Criterion {
        private String id;
        private int weight;
        private String requirement;
    }

}
