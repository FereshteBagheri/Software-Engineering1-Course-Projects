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
    public MatchResult match(Order newOrder) { return MatchResult.executed(null, new LinkedList<>()); }

    private void rollbackBuy(Order newOrder, LinkedList<Trade> trades){}

    private void rollbackSell(Order newOrder, LinkedList<Trade> trades) {}

    private void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {}

    @Override
    public MatchResult execute(Order order) {  
        if (order.getSide() == Side.BUY)
            order.getBroker().decreaseCreditBy(order.getValue());
        order.getSecurity().enqueueOrder(order);

        return MatchResult.executed(order, new LinkedList<>());
    }

    public void executeTriggeredStopLimitOrders(Security security, EventPublisher eventPublisher, int lastTradePrice, long requestId) {}

}
