package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.Message;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class Matcher {
    public MatchResult match(Order newOrder) {
        OrderBook orderBook = newOrder.getSecurity().getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();

        if (newOrder instanceof StopLimitOrder){
            StopLimitOrder order = (StopLimitOrder) newOrder;
            if (order.shouldActivate(order.getSecurity().getLastTradePrice()))
                newOrder = order.active();
            else
                return MatchResult.notActivated(order);
        }
        
        while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(newOrder);
            if (matchingOrder == null)
                break;

            Trade trade = new Trade(newOrder.getSecurity(), matchingOrder.getPrice(), Math.min(newOrder.getQuantity(), matchingOrder.getQuantity()), newOrder, matchingOrder);
            if (newOrder.getSide() == Side.BUY) {
                if (trade.buyerHasEnoughCredit())
                    trade.decreaseBuyersCredit();
                else {
                    rollbackTrades(newOrder, trades);
                    return MatchResult.notEnoughCredit();
                }
            }
            trade.increaseSellersCredit();
            trades.add(trade);

            if (newOrder.getQuantity() >= matchingOrder.getQuantity()) {
                newOrder.decreaseQuantity(matchingOrder.getQuantity());
                orderBook.removeFirst(matchingOrder.getSide());
                if (matchingOrder instanceof IcebergOrder icebergOrder) {
                    icebergOrder.decreaseQuantity(matchingOrder.getQuantity());
                    icebergOrder.replenish();
                    if (icebergOrder.getQuantity() > 0)
                        orderBook.enqueue(icebergOrder);
                }
            } else {
                matchingOrder.decreaseQuantity(newOrder.getQuantity());
                newOrder.makeQuantityZero();
            }
        }
        return MatchResult.executed(newOrder, trades);
    }

    private void rollbackBuy(Order newOrder, LinkedList<Trade> trades){
        newOrder.getBroker().increaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
        trades.forEach(trade ->
                trade.getSell().getBroker().decreaseCreditBy(trade.getTradedValue()));
        ListIterator<Trade> it = trades.listIterator(trades.size());
        while (it.hasPrevious()) {
            newOrder.getSecurity().getOrderBook().restoreSellOrder(it.previous().getSell());
        }
    }

    private void rollbackSell(Order newOrder, LinkedList<Trade> trades){
        newOrder.getBroker().decreaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
        trades.forEach(trade ->
                trade.getBuy().getBroker().increaseCreditBy(trade.getTradedValue()));
        ListIterator<Trade> it = trades.listIterator(trades.size());
        while (it.hasPrevious()) {
            newOrder.getSecurity().getOrderBook().restoreBuyOrder(it.previous().getBuy());
        }
    }

    private void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {
        if (newOrder.getSide() == Side.BUY)
            rollbackBuy(newOrder, trades);
        else
            rollbackSell(newOrder, trades);
    }

    public MatchResult execute(Order order) {
        int previous_quantity = order.getQuantity();
        MatchResult result = match(order);
        if (result.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT)
            return result;

        if (result.remainder().getQuantity() > 0) {
            if (!result.remainder().isMinimumQuantityExecuted() &&
                    (result.remainder().getQuantity() > (previous_quantity - order.getMinimumExecutionQuantity()))){
                rollbackTrades(order, result.trades());
                return MatchResult.minimumNotMatched();
            }

            if (order.getSide() == Side.BUY) {
                if (!order.getBroker().hasEnoughCredit(order.getValue())) {
                    rollbackTrades(order, result.trades());
                    return MatchResult.notEnoughCredit();
                }
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            
            if (result.remainder() instanceof StopLimitOrder)
                order.getSecurity().getStopOrderBook().enqueue((StopLimitOrder) result.remainder()); 
            else   
                order.getSecurity().getOrderBook().enqueue(result.remainder());
        }

        if (!result.remainder().isMinimumQuantityExecuted())
            result.remainder().setMinimumQuantityExecuted();

        if (!result.trades().isEmpty()) {
            for (Trade trade : result.trades()) {
                trade.getBuy().getShareholder().incPosition(trade.getSecurity(), trade.getQuantity());
                trade.getSell().getShareholder().decPosition(trade.getSecurity(), trade.getQuantity());
            }
        }
        return result;
    }

    public void executeActivatableStopLimitOrders(Security security, EventPublisher eventPublisher, int lastTradePrice, long requestId){
        security.setLastTradePrice(lastTradePrice);
        LinkedList<StopLimitOrder> activatableOrders = new LinkedList<StopLimitOrder>();
        
        MatchResult matchResult;
        while(true) {
            
            activatableOrders = security.getActivatableOrders(lastTradePrice);
            security.setLastTradePrice(lastTradePrice);

            if (activatableOrders.isEmpty())
                return;

            Order newOrder = activatableOrders.removeFirst().active();
            eventPublisher.publish(new OrderActivatedEvent(requestId, newOrder.getOrderId()));

            matchResult = execute(newOrder);
            
            // NOTE : these errors won't accured  

            // if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT) {
            //     eventPublisher.publish(new OrderRejectedEvent(requestId, newOrder.getOrderId(), List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
            //     return;
            // }
            // if (matchResult.outcome() == MatchingOutcome.MINIMUM_NOT_MATCHED) {
            //     eventPublisher.publish(new OrderRejectedEvent(requestId, newOrder.getOrderId(), List.of(Message.MINIMUM_EXECUTION_QUANTITY_NOT_MATCHED)));
            //     return;
            // }
            // if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_POSITIONS) {
            //     eventPublisher.publish(new OrderRejectedEvent(requestId, newOrder.getOrderId(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
            //     return;
            // }

            if (!matchResult.trades().isEmpty()) {
                lastTradePrice = matchResult.trades().getLast().getPrice();
                eventPublisher.publish(new OrderExecutedEvent(requestId, newOrder.getOrderId(), matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
            }
        }
    }
}