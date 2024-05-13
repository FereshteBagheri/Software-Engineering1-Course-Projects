package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.OrderActivatedEvent;
import ir.ramtung.tinyme.messaging.event.OrderExecutedEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class Matcher {
    public MatchResult match(Order newOrder) { return MatchResult.executed(null, new LinkedList<>()); }

    public MatchResult execute(Order order) { return MatchResult.executed(null, new LinkedList<>());}

    public void handleIcebergOrder(Order order) {
        if (order instanceof IcebergOrder icebergOrder) {
            icebergOrder.decreaseQuantity(order.getQuantity());
            icebergOrder.replenish();
            if (icebergOrder.getQuantity() > 0) {
                order.getSecurity().getOrderBook().enqueue(icebergOrder);
            }
        }
    }

    public void executeTriggeredStopLimitOrders(Security security, EventPublisher eventPublisher, int lastTradePrice, long requestId){
        LinkedList<StopLimitOrder> triggeredOrders = new LinkedList<StopLimitOrder>();
        
        MatchResult matchResult;
        while(true) {
            
            triggeredOrders.addAll(security.findTriggeredOrders(lastTradePrice));
            security.setLastTradePrice(lastTradePrice);

            if (triggeredOrders.isEmpty())
                return;
            StopLimitOrder stopOrder = triggeredOrders.removeFirst();
            Order newOrder = stopOrder.active();
            if (newOrder.getSide() == Side.BUY)
                newOrder.getBroker().increaseCreditBy(newOrder.getValue()); 

            eventPublisher.publish(new OrderActivatedEvent(requestId, newOrder.getOrderId()));
            matchResult = execute(newOrder);
            
            if (!matchResult.trades().isEmpty()) {
                lastTradePrice = matchResult.trades().getLast().getPrice();
                eventPublisher.publish(new OrderExecutedEvent(stopOrder.getRequestId(), newOrder.getOrderId(), matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
            }
        }
    }

    public void updatePositionsFromTrades(List<Trade> trades) {
        if (!trades.isEmpty()) {
            for (Trade trade : trades) {
                trade.getBuy().getShareholder().incPosition(trade.getSecurity(), trade.getQuantity());
                trade.getSell().getShareholder().decPosition(trade.getSecurity(), trade.getQuantity());
            }
        }
    }
}