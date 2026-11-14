package com.roms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrContextResponse {
    private boolean qrOrderingEnabled;
    private Long tableId;
    private String tableCode;
    private String area;
    private Long sessionId;
    private String restaurantName;
    private String address;
}
