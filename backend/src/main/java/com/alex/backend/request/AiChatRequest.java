package com.alex.backend.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data//@Data注解可以让Lombok 在编译阶段生成一些Java 样板代码，比如getter sette等
public class AiChatRequest {
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题不能超过2000个字符")
    private String question;
    @JsonProperty("document_id")//Java 代码继续使用 documentId，发送 JSON 时转换为document_id
    private Long documentId;    //为什么多此一举?  因为Java 采用小驼峰命名，Python 通常采用下划线命名


}