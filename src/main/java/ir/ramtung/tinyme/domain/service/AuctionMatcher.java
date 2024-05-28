package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@Service
public class AuctionMatcher extends Matcher {
    public MatchResult match(LinkedList<Order> buyOrders, LinkedList<Order> sellOrders, int openingPrice) {
        LinkedList<Trade> trades = new LinkedList<>();

        while (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {
            Order buyOrder = buyOrders.getFirst();
            Order sellOrder = sellOrders.getFirst();

            Trade trade = createTrade(buyOrder, sellOrder, openingPrice);

            if (buyOrder.getQuantity() == sellOrder.getQuantity()) {
                removeOrder(buyOrder, buyOrders);
                removeOrder(sellOrder, sellOrders);
                handleOrder(buyOrder, buyOrders);
                handleOrder(sellOrder, sellOrders);
            } else if (buyOrder.getQuantity() > sellOrder.getQuantity()) {
                buyOrder.decreaseQuantity(trade.getQuantity());
                removeOrder(sellOrder, sellOrders);
                handleOrder(sellOrder, sellOrders);
            } else { // buyOrder.getQuantity() < sellOrder.getQuantity()
                sellOrder.decreaseQuantity(trade.getQuantity());
                removeOrder(buyOrder, buyOrders);
                handleOrder(buyOrder, buyOrders);
            }

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

    @Override
    public MatchResult execute(Order order) {

        if (order.getSide() == Side.BUY && !order.getBroker().hasEnoughCredit(order.getValue()))
            return MatchResult.notEnoughCredit();

        if (order.getSide() == Side.BUY)
            order.getBroker().decreaseCreditBy(order.getValue());

        order.getSecurity().enqueueOrder(order);
        
        return MatchResult.executed(order, new LinkedList<>());
    }

    private void handleOrder(Order order, LinkedList<Order> orders) {
        handleIcebergOrder(order);
        if (order instanceof IcebergOrder icebergOrder && icebergOrder.getQuantity() > 0)
            enqueueOpenOrder(icebergOrder, orders);
    }

    private Trade createTrade(Order buyOrder, Order sellOrder, int openingPrice) {
        int quantityToTrade = Math.min(buyOrder.getQuantity(), sellOrder.getQuantity());
        return new Trade(buyOrder.getSecurity(), openingPrice, quantityToTrade, buyOrder, sellOrder);
    }
}
