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

            int quantityToTrade = Math.min(buyOrder.getQuantity(), sellOrder.getQuantity());
            Trade trade = new Trade(buyOrder.getSecurity(), openingPrice, quantityToTrade, buyOrder, sellOrder);

            if (buyOrder.getQuantity() == sellOrder.getQuantity()) {
                removeOrder(buyOrder);
                removeOrder(sellOrder);
                sellOrders.removeFirst();
                buyOrders.removeFirst();
                handleIcebergOrder(buyOrder);
                handleIcebergOrder(sellOrder);
                if (sellOrder instanceof IcebergOrder icebergOrder && icebergOrder.getQuantity() > 0)
                    enqueueOpenOrder(icebergOrder, sellOrders);
                if (buyOrder instanceof IcebergOrder icebergOrder && icebergOrder.getQuantity() > 0)
                    enqueueOpenOrder(icebergOrder, buyOrders);
            } else if (buyOrder.getQuantity() > sellOrder.getQuantity()) {
                buyOrder.decreaseQuantity(trade.getQuantity());
                removeOrder(sellOrder);
                sellOrders.removeFirst();
                handleIcebergOrder(sellOrder);
                if (sellOrder instanceof IcebergOrder icebergOrder && icebergOrder.getQuantity() > 0)
                    enqueueOpenOrder(icebergOrder, sellOrders);
            } else { // buyOrder.getQuantity() < sellOrder.getQuantity()
                sellOrder.decreaseQuantity(trade.getQuantity());
                removeOrder(buyOrder);
                buyOrders.removeFirst();
                handleIcebergOrder(buyOrder);
                if (buyOrder instanceof IcebergOrder icebergOrder && icebergOrder.getQuantity() > 0)
                    enqueueOpenOrder(icebergOrder, buyOrders);
            }

            adjustCredit(buyOrder, trade, openingPrice);
            trades.add(trade);
        }
        
        updatePositionsFromTrades(trades);
        return MatchResult.executed(null, trades);
    }

    private void removeOrder(Order order) {
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
        if (order.getSide() == Side.BUY)
            order.getBroker().decreaseCreditBy(order.getValue());
        order.getSecurity().enqueueOrder(order);

        return MatchResult.executed(order, new LinkedList<>());
    }
}
