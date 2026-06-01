package com.ruleforge.console.model;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.model.GeneralEntity;
import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * @author fred
 * @since 2020/08/18 9:21 PM
 */
@Data
@ToString(exclude = "messageItemList")
public class SaveProcessItemDto {
    private String projectVersion;
    private String packageId;
    private String flowId;

    private String traceId;

    private List<MessageItem> messageItemList;
    private GeneralEntity outputModel;
}
