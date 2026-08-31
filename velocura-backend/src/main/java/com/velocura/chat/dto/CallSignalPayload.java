package com.velocura.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CallSignalPayload {
    private Long conversationId;
    private Long fromUserId;
    private Long toUserId;
    private String type;    // "OFFER" | "ANSWER" | "ICE_CANDIDATE" | "CALL_END" | "CALL_REJECT" | "CALL_STARTED" | "CALL_ENDED"
    private String signal;  // JSON string of SDP offer/answer or ICE candidate
}
