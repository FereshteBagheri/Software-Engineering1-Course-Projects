package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.SecurirtyStateChangeRejectedEvent;
import ir.ramtung.tinyme.messaging.event.SecurityStateChangedEvent;
import ir.ramtung.tinyme.messaging.event.TradeEvent;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.messaging.request.Request;
import ir.ramtung.tinyme.repository.SecurityRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedList;

@Service
public class ChangeMatchingStateHandler extends ReqHandler {
    public ChangeMatchingStateHandler(SecurityRepository securityRepository, EventPublisher eventPublisher, ContinuousMatcher continuousMatcher, AuctionMatcher auctionMatcher, RequestControl requestControl) {
        this.securityRepository = securityRepository;
        this.eventPublisher = eventPublisher;
        this.continuousMatcher = continuousMatcher;
        this.auctionMatcher = auctionMatcher;
        this.requestControl = requestControl;
    }

    @Override
    protected void handleInvalidRequest(Request request, InvalidRequestException ex) {
        eventPublisher.publish(new SecurirtyStateChangeRejectedEvent(ex.getMessage()));
    }

    @Override
    protected void processRequest(ChangeMatchingStateRq request){
            Security security = securityRepository.findSecurityByIsin(request.getSecurityIsin());
            MatchingState target = request.getTargetState();
            MatchResult result = null;

            if (security.getState() == MatchingState.AUCTION)
                result = openSecurity(security);

            security.setMatchingState(target);
            eventPublisher.publish(new SecurityStateChangedEvent(security.getIsin(), target));

            if (result != null && !result.trades().isEmpty())
                activeStopLimitOrders(target, security, result.trades().getFirst().getPrice());
    };

    private MatchResult openSecurity(Security security) {
        CustomPair pair = security.findOpeningPrice();
        int openingPrice = pair.getFirst();
        LinkedList<Order> openBuyOrders = security.findOpenOrders(openingPrice, Side.BUY);
        LinkedList<Order> openSellOrders = security.findOpenOrders(openingPrice, Side.SELL);
        MatchResult matchResult = auctionMatcher.match(openBuyOrders, openSellOrders, openingPrice);
        publishTradeEvents(matchResult);
        return matchResult;
    }

    private void publishTradeEvents(MatchResult matchResult) {
        if (matchResult.trades().isEmpty())
            return;

        for (Trade trade : matchResult.trades())
            eventPublisher.publish(new TradeEvent(trade.getSecurity().getIsin(), trade.getPrice(), trade.getQuantity(),
                    trade.getBuy().getOrderId(), trade.getSell().getOrderId()));
    }

    public void activeStopLimitOrders(MatchingState state, Security security, int openingPrice) {
        if (state == MatchingState.AUCTION)
            auctionMatcher.executeTriggeredStopLimitOrders(security, eventPublisher, openingPrice);
        if (state == MatchingState.CONTINUOUS)
            continuousMatcher.executeTriggeredStopLimitOrders(security, eventPublisher, openingPrice);
    }
}

