package com.matrix.common.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.matrix.common.constant.Constant;
import jdk.jfr.Description;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Json 格式生成工具
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public class JSONSchemaUtil {

    /**
     * @description 生成类 简单 JSON Schema
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static JSONObject generate(Class<?> clazz) {
        return generate(clazz, true);
    }

    /**
     * @description 生成类 简单 JSON Schema
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static JSONObject generateSimple(Class<?> clazz) {
        return generate(clazz, false);
    }

    /**
     * @description 获取基础类型
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private static String getBasicType(Class<?> clazz) {
        if (clazz.equals(String.class)) {
            return "string";
        }
        if (clazz.equals(Integer.class) || clazz.equals(int.class) ||
                clazz.equals(Long.class) || clazz.equals(long.class)) {
            return "integer";
        }
        if (clazz.equals(Double.class) || clazz.equals(double.class) ||
                clazz.equals(Float.class) || clazz.equals(float.class)) {
            return "number";
        }
        if (clazz.equals(Boolean.class) || clazz.equals(boolean.class)) {
            return "boolean";
        }
        return null;
    }

    /**
     * @description 生成类 JSON Schema
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public static JSONObject generate(Class<?> clazz, boolean isComplete) {
        // 基础类型, 直接返回
        JSONObject schema = new JSONObject();
        String basicType = getBasicType(clazz);
        if (StringUtils.isNotBlank(basicType)) {
            schema.put("type", basicType);
            return schema;
        }

        // 非基础类型
        schema.put("type", "object");
        if (isComplete) {
            schema.put("title", clazz.getSimpleName());
            schema.put("description", "Object schema for " + clazz.getSimpleName());
        }

        JSONObject properties = new JSONObject();
        JSONArray requiredArray = new JSONArray();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            String fieldName = field.getName();
            // 跳过静态字段
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            // 生成字段的 schema
            properties.put(fieldName, fieldGenerate(field, isComplete));
            // 标记为必需字段
            requiredArray.add(fieldName);
        }
        schema.put("properties", properties);
        if (isComplete) {
            schema.put("required", requiredArray);
        }

        return schema;
    }

    /**
     * @description 生成字段 JSON Schema
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private static JSONObject fieldGenerate(Field field, boolean isComplete) {
        JSONObject fieldNode = new JSONObject();
        Class<?> fieldType = field.getType();
        Type genericType = field.getGenericType();

        // 设置描述
        Description description = AnnotatedElementUtils.getMergedAnnotation(field, Description.class);
        if (null != description) {
            fieldNode.put("description", description.value());
        }

        // 处理各种类型
        String basicType = getBasicType(fieldType);
        if (StringUtils.isNotBlank(basicType)) {
            fieldNode.put("type", basicType);
        }
        else if (fieldType.isArray()) {
            // 处理数组类型
            Class<?> componentType = fieldType.getComponentType();
            fieldNode.put("type", "array");
            fieldNode.put("items", generate(componentType, isComplete));
        }
        else if (genericType instanceof ParameterizedType parameterizedType) {
            // 处理泛型类型（List / Map 等）
            if (parameterizedType.getRawType() == List.class) {
                // 处理List类型，元素可能是 Class 或嵌套泛型（如 List<Map<String,Object>>）
                Type[] typeArgs = parameterizedType.getActualTypeArguments();
                fieldNode.put("type", "array");
                if (typeArgs.length > 0) {
                    Class<?> itemClass = resolveRawClass(typeArgs[0]);
                    if (itemClass == Map.class) {
                        // Map 元素直接描述为对象，避免生成无意义的 Map 类 Schema
                        fieldNode.put("items", new JSONObject().fluentPut("type", "object"));
                    } else {
                        fieldNode.put("items", generate(itemClass, isComplete));
                    }
                }
            } else {
                // 其他泛型类型（如 Map<String,Object>）按对象描述
                fieldNode.put("type", "object");
            }
        }
        else {
            // 其他对象类型
            fieldNode.put("type", "object");
            fieldNode.put("schema", generate(fieldType, isComplete));
        }

        return fieldNode;
    }

    /**
     * @description 解析泛型 Type 对应的原始 Class，兼容 Class 与嵌套 ParameterizedType
     * <p> 如 List<Map<String,Object>> 的元素类型解析为 Map.class </p>
     *
     * @author 陈晨
     */
    private static Class<?> resolveRawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class<?> clazz) {
                return clazz;
            }
        }
        return Object.class;
    }

    /**
     * @description 生成 JSON Schema
     *
     * @author 陈晨
     */
    public static JSONObject generate(Map<String, Object> inputSchema) {
        if (null == inputSchema) {
            inputSchema = new HashMap<>();
        }
        inputSchema.put(Constant.CLIENT_ID, Constant.CLIENT_ID_DESCRIPTION);

        // Schema
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject properties = new JSONObject();
        JSONArray requiredArray = new JSONArray();
        for (Map.Entry<String, Object> entry : inputSchema.entrySet()) {
            JSONObject fieldSchema = generateFieldSchema(entry.getValue());
            if (!fieldSchema.isEmpty()) {
                properties.put(entry.getKey(), fieldSchema);
                requiredArray.add(entry.getKey());
            }
        }
        schema.put("properties", properties);
        schema.put("required", requiredArray);
        return schema;
    }

    /**
     * @description 生成字段的 JSON Schema
     * <p>根据字段值生成对应的 JSON Schema 结构</p>
     *
     * @author 陈晨
     */
    private static JSONObject generateFieldSchema(Object fieldValue) {
        JSONObject fieldNode = new JSONObject();
        if (fieldValue == null) {
            fieldNode.put("type", "string");
            fieldNode.put("description", "无描述");
            return fieldNode;
        }

        // Map
        if (fieldValue instanceof Map) {
            Map<String, Object> nestedMap = (Map<String, Object>) fieldValue;
            return generate(nestedMap);
        }
        // 其他
        fieldNode.put("type", "string");
        fieldNode.put("description", fieldValue);
        return fieldNode;
    }

}


