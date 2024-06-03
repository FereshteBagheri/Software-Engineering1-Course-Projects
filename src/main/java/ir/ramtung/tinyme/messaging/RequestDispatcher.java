package ir.ramtung.tinyme.messaging;

import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.domain.service.ChangeMatchingStateHandler;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
public class RequestDispatcher {
    private final Logger log = Logger.getLogger(this.getClass().getName());
    private final OrderHandler orderHandler;
    private final ChangeMatchingStateHandler changeMatchingStateHandler;

    public RequestDispatcher(OrderHandler orderHandler, ChangeMatchingStateHandler changeMatchingStateHandler) {
        this.orderHandler = orderHandler;
        this.changeMatchingStateHandler = changeMatchingStateHandler;
    }

    @JmsListener(destination = "${requestQueue}", selector = "_type='ir.ramtung.tinyme.messaging.request.EnterOrderRq'")
    public void receiveEnterOrderRq(EnterOrderRq enterOrderRq)  {
        log.info("Received message: " + enterOrderRq);
        orderHandler.handleRequest(enterOrderRq);
    }

    @JmsListener(destination = "${requestQueue}", selector = "_type='ir.ramtung.tinyme.messaging.request.DeleteOrderRq'")
    public void receiveDeleteOrderRq(DeleteOrderRq deleteOrderRq) {
        log.info("Received message: " + deleteOrderRq);
        orderHandler.handleDeleteOrder(deleteOrderRq);
    }

    @JmsListener(destination = "${requestQueue}", selector = "_type='ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq'")
    public void receiveChangeMatchingSateRq(ChangeMatchingStateRq changeMatchingStateRq) {
        log.info("Received message: " + changeMatchingStateRq);
        changeMatchingStateHandler.handleRequest(changeMatchingStateRq);
    }
}
