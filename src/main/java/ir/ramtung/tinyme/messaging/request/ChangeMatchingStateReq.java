package ir.ramtung.tinyme.messaging.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChangeMatchingStateReq {
    private String securityIsin;
    private MatchingState targetState;
}
