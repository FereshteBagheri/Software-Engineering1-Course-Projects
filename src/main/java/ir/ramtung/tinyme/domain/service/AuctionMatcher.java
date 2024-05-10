package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.stream.Collectors;

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
            } else if (buyOrder.getQuantity() > sellOrder.getQuantity()) {
                buyOrder.decreaseQuantity(trade.getQuantity());
                sellOrder.getSecurity().removeOrderByOrderId(sellOrder, sellOrder.getSide(), sellOrder.getOrderId());
                sellOrders.removeFirst();
                handleIcebergOrder(sellOrder);
            } else { // buyOrder.getQuantity() < sellOrder.getQuantity()
                sellOrder.decreaseQuantity(trade.getQuantity());
                buyOrder.getSecurity().removeOrderByOrderId(buyOrder, buyOrder.getSide(), buyOrder.getOrderId());
                buyOrders.removeFirst();
                handleIcebergOrder(buyOrder);
            }

            adjustCredit(buyOrder, trade, openingPrice);
            trades.add(trade);
        }

        return MatchResult.executed(null, trades);
    }

    private void removeOrder(Order order) {
        order.getSecurity().removeOrderByOrderId(order, order.getSide(), order.getOrderId());
    }

    private void adjustCredit(Order buyOrder, Trade trade, int openingPrice) {
        buyOrder.getBroker().increaseCreditBy((buyOrder.getPrice() - openingPrice) * trade.getQuantity());
        trade.increaseSellersCredit();
    }

    @Override
    public MatchResult execute(Order order) {  
        if (order.getSide() == Side.BUY)
            order.getBroker().decreaseCreditBy(order.getValue());
        order.getSecurity().enqueueOrder(order);

        return MatchResult.executed(order, new LinkedList<>());
    }
}
