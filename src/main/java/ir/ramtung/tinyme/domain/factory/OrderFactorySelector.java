package ir.ramtung.tinyme.domain.factory;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;

public class OrderFactorySelector {
    public static OrderFactory getFactory(EnterOrderRq request) {
        if (request.getStopPrice() != 0)
            return StopLimitOrderFactory.getInstance();
        else if (request.getPeakSize() == 0)
            return RegularOrderFactory.getInstance();
        else
            return IcebergOrderFactory.getInstance();
    }
}