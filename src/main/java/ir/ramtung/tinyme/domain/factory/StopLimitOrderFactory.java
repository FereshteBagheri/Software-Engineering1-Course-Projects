package ir.ramtung.tinyme.domain.factory;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.request.*;

public class StopLimitOrderFactory implements OrderFactory {

    private static StopLimitOrderFactory instance;

    private StopLimitOrderFactory() {
    }

    public static synchronized StopLimitOrderFactory getInstance() {
        if (instance == null) {
            instance = new StopLimitOrderFactory();
        }
        return instance;
    }

    @Override
    public Order createOrder(EnterOrderRq request, Security security, Broker broker, Shareholder shareholder) {
        return new StopLimitOrder(
                request.getOrderId(),
                security,
                request.getSide(),
                request.getQuantity(),
                request.getPrice(),
                broker,
                shareholder,
                request.getEntryTime(),
                request.getStopPrice(),
                request.getRequestId()
        );
    }
}
