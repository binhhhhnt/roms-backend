package com.roms.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatHistoryItem {

    @Size(max = 20, message = "Sender must not exceed 20 characters")
    private String sender;

    @Size(max = 1000, message = "History message must not exceed 1000 characters")
    private String text;
}
