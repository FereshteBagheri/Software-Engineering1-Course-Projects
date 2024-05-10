package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.stream.Collectors;

@Service
public class Matcher {
    public MatchResult match(Order newOrder) { return MatchResult.executed(null, new LinkedList<>()); }

    private void rollbackBuy(Order newOrder, LinkedList<Trade> trades){}

    private void rollbackSell(Order newOrder, LinkedList<Trade> trades) {}

    private void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {}

    public MatchResult execute(Order order) { return MatchResult.executed(null, new LinkedList<>());}

    public void executeTriggeredStopLimitOrders(Security security, EventPublisher eventPublisher, int lastTradePrice, long requestId) {}

    public void handleIcebergOrder(Order order) {
        if (order instanceof IcebergOrder icebergOrder) {
            icebergOrder.decreaseQuantity(order.getQuantity());
            icebergOrder.replenish();
            if (icebergOrder.getQuantity() > 0) {
                order.getSecurity().getOrderBook().enqueue(icebergOrder);
            }
        }
    }
}