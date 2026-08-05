package com.matrix.common.dto.model;


import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 消息
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message implements Serializable {
    @Serial
    private static final long serialVersionUID = -8169372345888213071L;

    private String role;
    private String content;
    private String reasoning_content;
    private List<Response.ToolCall> tool_calls;
    private String tool_call_id;

    // 关键：使用 Object 类型接收，同时兼容 String 和 Array
    /** 设置Content属性值 */
    public void setContent(Object contentObj) {
        if (contentObj == null) {
            this.content = null;
        } else if (contentObj instanceof String) {
            this.content = (String) contentObj;
        } else if (contentObj instanceof List) {
            List<?> list = (List<?>) contentObj;
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item != null) {
                    sb.append(item);
                }
            }
            this.content = sb.toString();
        } else {
            // 其他类型（数字、布尔等）统一转为字符串
            this.content = contentObj.toString();
        }
    }

    /** 清空数据或缓存 */
    public void clear() {
        this.role = null;
        this.content = null;
        this.reasoning_content = null;
        this.tool_calls = null;
        this.tool_call_id = null;
    }

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }

    /** system操作 */
    public static Message system(String content) {
        return Message.builder()
                .role(Role.SYSTEM)
                .content(content)
                .build();
    }

    /** user操作 */
    public static Message user(String content) {
        return Message.builder()
                .role(Role.USER)
                .content(content)
                .build();
    }

    /** assistant操作 */
    public static Message assistant(String content) {
        return Message.builder()
                .role(Role.ASSISTANT)
                .content(content)
                .build();
    }

    /** 转换为olCall格式 */
    public static Message toolCall(Response.ToolCall... toolCalls) {
        return Message.builder()
                .role(Role.ASSISTANT)
                .tool_calls(List.of(toolCalls))
                .build();
    }

    /** 转换为ol格式 */
    public static Message tool(String toolCallId, String content) {
        return Message.builder()
                .role(Role.TOOL)
                .content(content)
                .tool_call_id(toolCallId)
                .build();
    }

//    public static Message latestReminder(String content) {
//        return Message.builder()
//                .role(Role.LATEST_REMINDER)
//                .content(content)
//                .build();
//    }

//    public static Message image(String content, String imageUrl) {
//        JSONArray array = new JSONArray();
//        array.add(new ImageContent(imageUrl));
//        array.add(new TextContent(content));
//        return new Message(ROLE_USER, array.toJSONString());
//    }
//
//    public static Message video(String content, String... video) {
//        JSONArray array = new JSONArray();
//        array.add(new VideoContent(video));
//        array.add(new TextContent(content));
//        return new Message(ROLE_USER, array.toJSONString());
//    }

}


