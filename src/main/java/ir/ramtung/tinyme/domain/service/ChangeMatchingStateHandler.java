package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.SecurirtyStateChangeRejectedEvent;
import ir.ramtung.tinyme.messaging.event.SecurityStateChangedEvent;
import ir.ramtung.tinyme.messaging.event.TradeEvent;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.repository.SecurityRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedList;

@Service
public class ChangeMatchingStateHandler extends ReqHandler {
    public ChangeMatchingStateHandler(SecurityRepository securityRepository, EventPublisher eventPublisher, ContinuousMatcher continuousMatcher, AuctionMatcher auctionMatcher) {
        this.securityRepository = securityRepository;
        this.eventPublisher = eventPublisher;
        this.continuousMatcher = continuousMatcher;
        this.auctionMatcher = auctionMatcher;
    }


    public void handleChangeMatchingStateRq(ChangeMatchingStateRq changeMatchingStateRq) {
        try {
            validateSecurity(changeMatchingStateRq.getSecurityIsin());
            Security security = securityRepository.findSecurityByIsin(changeMatchingStateRq.getSecurityIsin());
            MatchingState target = changeMatchingStateRq.getTargetState();
            if (security.getState() == MatchingState.AUCTION)
                openSecurity(security, changeMatchingStateRq.getRequestId(), target);

            security.setMatchingState(target);
            eventPublisher.publish(new SecurityStateChangedEvent(security.getIsin(), target));

        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new SecurirtyStateChangeRejectedEvent(changeMatchingStateRq.getRequestId(),ex.getMessage()));
        }
    }

    private void validateSecurity(String isin) throws InvalidRequestException {
        Security security = securityRepository.findSecurityByIsin(isin);
        if (security == null)
            throw new InvalidRequestException(Message.UNKNOWN_SECURITY_ISIN);
    }

    private void openSecurity(Security security, long requestId, MatchingState target) {
        CustomPair pair = security.findOpeningPrice();
        int openingPrice = pair.getFirst();
        LinkedList<Order> openBuyOrders = security.findOpenOrders(openingPrice, Side.BUY);
        LinkedList<Order> openSellOrders = security.findOpenOrders(openingPrice, Side.SELL);
        MatchResult matchResult = auctionMatcher.match(openBuyOrders, openSellOrders, openingPrice);
        publishTradeEvents(matchResult);

        if (!matchResult.trades().isEmpty() && target == MatchingState.AUCTION)
            auctionMatcher.executeTriggeredStopLimitOrders(security, eventPublisher, openingPrice);
        if (!matchResult.trades().isEmpty() && target == MatchingState.CONTINUOUS)
            continuousMatcher.executeTriggeredStopLimitOrders(security, eventPublisher, openingPrice);
    }

    private void publishTradeEvents(MatchResult matchResult) {
        if (matchResult.trades().isEmpty())
            return;

        for (Trade trade : matchResult.trades())
            eventPublisher.publish(new TradeEvent(trade.getSecurity().getIsin(), trade.getPrice(), trade.getQuantity(),
                    trade.getBuy().getOrderId(), trade.getSell().getOrderId()));
    }
}
