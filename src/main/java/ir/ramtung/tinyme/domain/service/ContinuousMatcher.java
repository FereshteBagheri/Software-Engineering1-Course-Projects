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
            updateOrdersAfterTrade(newOrder, matchingOrder,  matchingOrder.getQuantity(), orderBook);
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


    public MatchResult addOrdertoOrderBook(Order remainder,LinkedList<Trade> trades, int previousQuantity) {
        if (remainder.getQuantity() > 0) {
            if (!remainder.isMinimumQuantityExecuted() &&
                    (remainder.getQuantity() > (previousQuantity - remainder.getMinimumExecutionQuantity()))){
                rollbackTrades(remainder, trades);
                return MatchResult.minimumNotMatched();
            }

            if (remainder.getSide() == Side.BUY) {
                if (!remainder.getBroker().hasEnoughCredit(remainder.getValue())) {
                    rollbackTrades(remainder, trades);
                    return MatchResult.notEnoughCredit();
                }
                remainder.getBroker().decreaseCreditBy(remainder.getValue());
            }
            remainder.getSecurity().enqueueOrder(remainder);
        }

        if (!remainder.isMinimumQuantityExecuted())
            remainder.setMinimumQuantityExecuted();

        return MatchResult.executed(remainder, trades);
    }

    private void updateOrdersAfterTrade(Order newOrder, Order matchingOrder, int matchingQuantity, OrderBook orderBook) {
        if (newOrder.getQuantity() >= matchingQuantity) {
            newOrder.decreaseQuantity(matchingQuantity);
            orderBook.removeFirst(matchingOrder.getSide());
            handleIcebergOrder(matchingOrder);
        } else {
            matchingOrder.decreaseQuantity(newOrder.getQuantity());
            newOrder.makeQuantityZero();
        }
    }



}
