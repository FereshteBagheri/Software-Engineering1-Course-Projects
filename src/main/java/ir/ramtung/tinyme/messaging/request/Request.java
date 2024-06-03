package ir.ramtung.tinyme.messaging.request;


import lombok.Data;
import lombok.NoArgsConstructor;

@Data

public class Request {
    protected long requestId;
    protected long orderId;
    protected String securityIsin;
}
