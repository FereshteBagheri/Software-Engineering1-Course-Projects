package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.OpeningPriceEvent;
import ir.ramtung.tinyme.messaging.event.SecurirtyStateChangeRejectedEvent;
import ir.ramtung.tinyme.messaging.event.SecurityStateChangedEvent;
import ir.ramtung.tinyme.messaging.event.TradeEvent;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateReq;
import ir.ramtung.tinyme.messaging.request.MatchingState;

import java.util.LinkedList;

public class ChangeMatchingStateHandler extends ReqHandler {

    public void handleChangeMatchingStateRq(ChangeMatchingStateReq changeMatchingStateReq) {
        try {
            validateSecurity(changeMatchingStateReq.getSecurityIsin());
            Security security = securityRepository.findSecurityByIsin(changeMatchingStateReq.getSecurityIsin());
            MatchingState target = changeMatchingStateReq.getTargetState();
            if (security.getState() == MatchingState.AUCTION)
                openSecurity(security, changeMatchingStateReq.getRequestId());

            security.setMatchingState(target);
            eventPublisher.publish(new SecurityStateChangedEvent(security.getIsin(), target));

        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new SecurirtyStateChangeRejectedEvent(changeMatchingStateReq.getRequestId(),ex.getMessage()));
        }
    }

    private void validateSecurity(String isin) throws InvalidRequestException {
        Security security = securityRepository.findSecurityByIsin(isin);
        if (security == null)
            throw new InvalidRequestException(Message.UNKNOWN_SECURITY_ISIN);
    }

    private void openSecurity(Security security, long requestId) {
        CustomPair pair = security.findOpeningPrice();
        int openingPrice = pair.getFirst();
        eventPublisher.publish(new OpeningPriceEvent(security.getIsin(), openingPrice, pair.getSecond()));
        LinkedList<Order> openBuyOrders = security.findOpenOrders(openingPrice, Side.BUY);
        LinkedList<Order> openSellOrders = security.findOpenOrders(openingPrice, Side.SELL);
        MatchResult matchResult = auctionMatcher.match(openBuyOrders, openSellOrders, openingPrice);
        publishTradeEvents(matchResult);
        if (!matchResult.trades().isEmpty())
            auctionMatcher.executeTriggeredStopLimitOrders(security, eventPublisher, openingPrice, requestId);
    }

    private void publishTradeEvents(MatchResult matchResult) {
        if (matchResult.trades().isEmpty())
            return;

        for (Trade trade : matchResult.trades())
            eventPublisher.publish(new TradeEvent(trade.getSecurity().getIsin(), trade.getPrice(), trade.getQuantity(),
                    trade.getBuy().getOrderId(), trade.getSell().getOrderId()));
    }
}
