package com.ruleforge.decision.model;

import com.alibaba.fastjson2.JSONObject;
import com.ruleforge.model.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * @author Fred
 * @since 2019-09-25 1:28 PM
 */
public abstract class BaseModel {
    public final static Logger logger = LoggerFactory.getLogger(BaseModel.class);

    public JSONObject toJsonObject() {
        JSONObject jsonObject = new JSONObject();

        Class<? extends BaseModel> obj = this.getClass();
        Field[] fields = obj.getDeclaredFields();
        JSONObject jsonObjectField = new JSONObject();
        for (Field field : fields) {
            Label label = field.getAnnotation(Label.class);
            if (label != null) {
                boolean flag = field.isAccessible();
                try {
                    field.setAccessible(true);
                    Object o = field.get(this);
                    jsonObjectField.put(field.getName(), o);
                } catch (Exception e) {
                    logger.error("toJson error", e);
                }
                field.setAccessible(flag);
            }
        }

        jsonObject.put(obj.getName(), jsonObjectField);
        return jsonObject;
    }
}
