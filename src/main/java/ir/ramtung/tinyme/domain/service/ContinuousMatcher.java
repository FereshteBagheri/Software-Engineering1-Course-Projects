package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;


import java.util.LinkedList;
import java.util.ListIterator;

@Service
public class ContinuousMatcher extends Matcher{
    public MatchResult match(Order newOrder) {
        OrderBook orderBook = newOrder.getSecurity().getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();

        if (newOrder instanceof StopLimitOrder stopLimitOrder) {
            if (stopLimitOrder.isTriggered(stopLimitOrder.getSecurity().getLastTradePrice()))
                newOrder = stopLimitOrder.active();
            else
                return MatchResult.notActivated(stopLimitOrder);
        }

        while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(newOrder);
            if (matchingOrder == null)
                break;

            Trade trade = createTrade(newOrder, matchingOrder, matchingOrder.getPrice());
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
                handleIcebergOrder(matchingOrder);
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
                    (result.remainder().getQuantity() > (previous_quantity - result.remainder().getMinimumExecutionQuantity()))){
                rollbackTrades(result.remainder(), result.trades());
                return MatchResult.minimumNotMatched();
            }

            if (order.getSide() == Side.BUY) {
                if (!order.getBroker().hasEnoughCredit(result.remainder().getValue())) {
                    rollbackTrades(result.remainder(), result.trades());
                    return MatchResult.notEnoughCredit();
                }
                order.getBroker().decreaseCreditBy(result.remainder().getValue());
            }
            order.getSecurity().enqueueOrder(result.remainder());
        }

        if (!result.remainder().isMinimumQuantityExecuted())
            result.remainder().setMinimumQuantityExecuted();

        updatePositionsFromTrades(result.trades());
        return result;
    }

}
