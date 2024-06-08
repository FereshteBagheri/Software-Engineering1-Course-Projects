package ir.ramtung.tinyme.domain.factory;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.request.*;

public class RegularOrderFactory implements OrderFactory {
    private static RegularOrderFactory instance;

    private RegularOrderFactory() {
    }

    public static synchronized RegularOrderFactory getInstance() {
        if (instance == null) {
            instance = new RegularOrderFactory();
        }
        return instance;
    }

    @Override
    public Order createOrder(EnterOrderRq request, Security security, Broker broker, Shareholder shareholder) {
        return new Order(
                request.getOrderId(),
                security,
                request.getSide(),
                request.getQuantity(),
                request.getPrice(),
                broker,
                shareholder,
                request.getEntryTime(),
                request.getMinimumExecutionQuantity(),
                false
        );
    }
}
