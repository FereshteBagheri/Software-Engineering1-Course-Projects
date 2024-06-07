package ir.ramtung.tinyme.messaging.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChangeMatchingStateRq extends Request {
    private MatchingState targetState;

    public ChangeMatchingStateRq(String securityIsin, MatchingState state) {
        this.securityIsin = securityIsin;
        this.targetState = state;
    }
}
