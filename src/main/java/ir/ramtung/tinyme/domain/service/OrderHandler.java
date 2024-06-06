package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.*;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderHandler extends ReqHandler {

    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;
    SecurityRepository securityRepository;

    public OrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository,
            ShareholderRepository shareholderRepository,
            EventPublisher eventPublisher, ContinuousMatcher continuousMatcher, AuctionMatcher auctionMatcher,
            RequestControl requestControl) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.eventPublisher = eventPublisher;
        this.continuousMatcher = continuousMatcher;
        this.auctionMatcher = auctionMatcher;
        this.requestControl = requestControl;
    }

    @Override
    protected void handleInvalidRequest(Request request, InvalidRequestException ex) {
        eventPublisher.publish(new OrderRejectedEvent(request.getRequestId(), request.getOrderId(), ex.getReasons()));
    }

    @Override
    public void processRequest(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        Broker broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
        Shareholder shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());

        Matcher matcher = (security.getState() == MatchingState.AUCTION) ? auctionMatcher : continuousMatcher;
        MatchResult matchResult;

        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            matchResult = security.newOrder(enterOrderRq, broker, shareholder, matcher);
        else
            matchResult = security.updateOrder(enterOrderRq, matcher);

        publishEnterOrderReqEvents(matchResult, enterOrderRq);
        if (!matchResult.trades().isEmpty())
            matcher.executeTriggeredStopLimitOrders(security, eventPublisher,
                    matchResult.trades().getLast().getPrice());

        publishOpenPriceEvent(security);
    }

    @Override
    protected void processRequest(DeleteOrderRq request) throws InvalidRequestException {
        Security security = securityRepository.findSecurityByIsin(request.getSecurityIsin());
        security.deleteOrder(request);
        eventPublisher.publish(new OrderDeletedEvent(request.getRequestId(), request.getOrderId()));
        publishOpenPriceEvent(security);
    };

    private void publishEnterOrderReqEvents(MatchResult matchResult, EnterOrderRq enterOrderRq) {
        if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
            return;
        }
        if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_POSITIONS) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
            return;
        }
        if (matchResult.outcome() == MatchingOutcome.MINIMUM_NOT_MATCHED) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    List.of(Message.MINIMUM_EXECUTION_QUANTITY_NOT_MATCHED)));
            return;
        }

        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            eventPublisher.publish(new OrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
        else
            eventPublisher.publish(new OrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));

        if (matchResult.outcome() == MatchingOutcome.NOT_ACTIVATED && enterOrderRq.getStopPrice() != 0)
            return;

        if ((matchResult.outcome() == MatchingOutcome.EXECUTED && matchResult.remainder() != null)
                && enterOrderRq.getStopPrice() != 0)
            eventPublisher.publish(new OrderActivatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));

        if (!matchResult.trades().isEmpty())
            eventPublisher.publish(new OrderExecutedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
    }

    void publishOpenPriceEvent(Security security) {
        if (security.getState() == MatchingState.CONTINUOUS)
            return;
        CustomPair pair = security.findOpeningPrice();
        eventPublisher.publish(new OpeningPriceEvent(security.getIsin(), pair.getFirst(), pair.getSecond()));
    }
}
