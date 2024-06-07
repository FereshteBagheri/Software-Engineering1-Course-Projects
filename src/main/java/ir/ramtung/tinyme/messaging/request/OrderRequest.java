package ir.ramtung.tinyme.messaging.request;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequest extends Request {
    protected long requestId;
    protected long orderId;
}
