package ir.ramtung.tinyme.domain.factory;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.request.*;

public class IcebergOrderFactory implements OrderFactory {

    private static IcebergOrderFactory instance;

    private IcebergOrderFactory() {
    }

    public static synchronized IcebergOrderFactory getInstance() {
        if (instance == null) {
            instance = new IcebergOrderFactory();
        }
        return instance;
    }
    @Override
    public Order createOrder(EnterOrderRq request, Security security, Broker broker, Shareholder shareholder) {
        return new IcebergOrder(
                request.getOrderId(),
                security,
                request.getSide(),
                request.getQuantity(),
                request.getPrice(),
                broker,
                shareholder,
                request.getEntryTime(),
                request.getPeakSize(),
                request.getMinimumExecutionQuantity(),
                false
        );
    }
}
