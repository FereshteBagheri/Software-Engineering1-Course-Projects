package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@Service
public class AuctionMatcher extends Matcher {
    public MatchResult match(Order order) { return MatchResult.executed(order, new LinkedList<>());}

    public MatchResult match(LinkedList<Order> buyOrders, LinkedList<Order> sellOrders, int openingPrice) {
        LinkedList<Trade> trades = new LinkedList<>();

        while (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {
            Order buyOrder = buyOrders.getFirst();
            Order sellOrder = sellOrders.getFirst();

            Trade trade = createTrade(buyOrder, sellOrder, openingPrice);
            updateOrdersAfterTrade(buyOrder, sellOrder, buyOrders, sellOrders, trade.getQuantity());
            adjustCredit(buyOrder, trade, openingPrice);
            trades.add(trade);
        }
        
        updatePositionsFromTrades(trades);
        return MatchResult.executed(null, trades);
    }

    private void removeOrder(Order order, LinkedList<Order> orders) {
        orders.removeFirst();
        order.getSecurity().removeOrderByOrderId(order, order.getSide(), order.getOrderId());
    }

    private void adjustCredit(Order buyOrder, Trade trade, int openingPrice) {
        buyOrder.getBroker().increaseCreditBy((buyOrder.getPrice() - openingPrice) * trade.getQuantity());
        trade.increaseSellersCredit();
    }

    private void enqueueOpenOrder(Order order, List<Order> openOrders) {
        ListIterator<Order> it = openOrders.listIterator();
        while (it.hasNext()) {
            if (order.queuesBefore(it.next())) {
                it.previous();
                break;
            }
        }
        order.queue();
        it.add(order);
    }

    private void handleIcebergOrder(Order order, LinkedList<Order> orders) {
        handleIcebergOrder(order);
        if (order instanceof IcebergOrder icebergOrder && icebergOrder.getQuantity() > 0)
            enqueueOpenOrder(icebergOrder, orders);
    }

    private void updateOrdersAfterTrade(Order buyOrder, Order sellOrder, LinkedList<Order> buyOrders, LinkedList<Order> sellOrders, int tradeQuantity) {
        if (buyOrder.getQuantity() == sellOrder.getQuantity()) {
                handleOrderCompletion(buyOrder, buyOrders);
                handleOrderCompletion(sellOrder, sellOrders);
            } else if (buyOrder.getQuantity() > sellOrder.getQuantity()) {
                buyOrder.decreaseQuantity(tradeQuantity);
                handleOrderCompletion(sellOrder, sellOrders);
            } else {
                sellOrder.decreaseQuantity(tradeQuantity);
                handleOrderCompletion(buyOrder, buyOrders);
            }
    }

    public MatchResult addOrdertoOrderBook(Order remainder,LinkedList<Trade> trades, int previousQuantity) {
        if (remainder.getSide() == Side.BUY && !remainder.getBroker().hasEnoughCredit(remainder.getValue()))
            return MatchResult.notEnoughCredit();

        if (remainder.getSide() == Side.BUY)
            remainder.getBroker().decreaseCreditBy(remainder.getValue());

        remainder.getSecurity().enqueueOrder(remainder);
        return MatchResult.executed(remainder, trades);
    }

    private void handleOrderCompletion(Order order, LinkedList<Order> orders) {
        removeOrder(order, orders);
        handleIcebergOrder(order, orders);
    }
}
