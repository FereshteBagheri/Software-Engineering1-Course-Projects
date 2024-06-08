package ir.ramtung.tinyme.domain.factory;


import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.request.*;


public interface OrderFactory {

    Order createOrder(EnterOrderRq request, Security security, Broker broker, Shareholder shareholder);
}
