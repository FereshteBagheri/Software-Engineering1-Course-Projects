package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.OrderActivatedEvent;
import ir.ramtung.tinyme.messaging.event.OrderExecutedEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class Matcher {
    public abstract MatchResult match(Order newOrder);

    public abstract MatchResult addOrderToOrderBook(Order remainder, LinkedList<Trade> trades, int previousQuantity);

    public MatchResult execute(Order order) {
        int previousQuantity = order.getQuantity();
        MatchResult result = match(order);
        if (result.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT)
            return result;

        MatchResult enqueueRemainderResult = addOrderToOrderBook(result.remainder(), result.trades(), previousQuantity);
        if (result.outcome() != MatchingOutcome.NOT_ACTIVATED || enqueueRemainderResult.outcome() != MatchingOutcome.EXECUTED)
            result = enqueueRemainderResult;
        if (result.outcome() != MatchingOutcome.EXECUTED)
            return result;

        updatePositionsFromTrades(result.trades());
        return result;
    }

    public void handleIcebergOrder(Order order) {
        if (order instanceof IcebergOrder icebergOrder) {
            icebergOrder.decreaseQuantity(order.getQuantity());
            icebergOrder.replenish();
            if (icebergOrder.getQuantity() > 0)
                order.getSecurity().getOrderBook().enqueue(icebergOrder);
        }
    }

    public void executeTriggeredStopLimitOrders(Security security, EventPublisher eventPublisher, int lastTradePrice) {
        LinkedList<StopLimitOrder> triggeredOrders = new LinkedList<StopLimitOrder>();

        MatchResult matchResult;
        while (true) {

            triggeredOrders.addAll(security.findTriggeredOrders(lastTradePrice));
            security.setLastTradePrice(lastTradePrice);

            if (triggeredOrders.isEmpty())
                return;
            StopLimitOrder stopOrder = triggeredOrders.removeFirst();
            Order newOrder = stopOrder.active();
            if (newOrder.getSide() == Side.BUY)
                newOrder.getBroker().increaseCreditBy(newOrder.getValue());

            eventPublisher.publish(new OrderActivatedEvent(stopOrder.getRequestId(), newOrder.getOrderId()));
            matchResult = execute(newOrder);

            if (!matchResult.trades().isEmpty()) {
                lastTradePrice = matchResult.trades().getLast().getPrice();
                eventPublisher.publish(new OrderExecutedEvent(stopOrder.getOrderId(), newOrder.getOrderId(),
                        matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
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

    public Trade createTrade(Order buyOrder, Order sellOrder, int matchingPrice) {
        int quantityToTrade = Math.min(buyOrder.getQuantity(), sellOrder.getQuantity());
        return new Trade(buyOrder.getSecurity(), matchingPrice, quantityToTrade, buyOrder, sellOrder);
    }
}