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

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderHandler extends ReqHandler {

    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;
    SecurityRepository securityRepository;
    public OrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository,
                        EventPublisher eventPublisher, ContinuousMatcher continuousMatcher, AuctionMatcher auctionMatcher) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.eventPublisher = eventPublisher;
        this.continuousMatcher = continuousMatcher;
        this.auctionMatcher = auctionMatcher;
    }

    @Override
    protected void handleInvalidRequest(Request request, InvalidRequestException ex) {
        eventPublisher.publish(new OrderRejectedEvent(request.getRequestId(), request.getOrderId(), ex.getReasons()));
    }

    protected void validateRequest(EnterOrderRq request) throws InvalidRequestException{
        validateEnterOrderRq(request);
        validateAuctionStateRules(request, securityRepository.findSecurityByIsin(request.getSecurityIsin()));
    };

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
                matcher.executeTriggeredStopLimitOrders(security , eventPublisher, matchResult.trades().getLast().getPrice());

            publishOpenPriceEvent(security);
    }

    public void handleDeleteOrder(DeleteOrderRq deleteOrderRq) {
        try {
            validateDeleteOrderRq(deleteOrderRq);
            Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
            security.deleteOrder(deleteOrderRq);
            eventPublisher.publish(new OrderDeletedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId()));
            publishOpenPriceEvent(security);
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    private void validateEnterOrderRq(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();

        validateOrderDetails(enterOrderRq, errors);
        validateSecurity(enterOrderRq, errors);
        validateBrokerAndShareholder(enterOrderRq, errors);

        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    private void validateOrderDetails(EnterOrderRq enterOrderRq, List<String> errors) {
        if (enterOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (enterOrderRq.getQuantity() <= 0)
            errors.add(Message.ORDER_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getPrice() <= 0)
            errors.add(Message.ORDER_PRICE_NOT_POSITIVE);
        if (enterOrderRq.getStopPrice() < 0)
            errors.add(Message.INVALID_STOP_PRICE);
        if (enterOrderRq.getStopPrice() != 0 && enterOrderRq.getMinimumExecutionQuantity() != 0)
            errors.add(Message.INVALID_STOP_LIMIT_ORDER_WITH_MIN_EXECUTION_QUANTITY);
        if (enterOrderRq.getStopPrice() != 0 &&  enterOrderRq.getPeakSize() != 0)
            errors.add(Message.INVALID_STOP_LIMIT_ORDER_WITH_PEAKSIZE);
        if (enterOrderRq.getMinimumExecutionQuantity() < 0 || (enterOrderRq.getMinimumExecutionQuantity() > enterOrderRq.getQuantity() && enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER))
            errors.add(Message.INVALID_MINIMUM_EXECUTION_QUANTITY);
        if (enterOrderRq.getPeakSize() < 0 || enterOrderRq.getPeakSize() >= enterOrderRq.getQuantity())
            errors.add(Message.INVALID_PEAK_SIZE);
    }


    private void validateSecurity(EnterOrderRq enterOrderRq, List<String> errors) {
        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        else {
            if (enterOrderRq.getQuantity() % security.getLotSize() != 0)
                errors.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
            if (enterOrderRq.getPrice() % security.getTickSize() != 0)
                errors.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
        }
    }

    private void validateBrokerAndShareholder(EnterOrderRq enterOrderRq, List<String> errors) {
        if (brokerRepository.findBrokerById(enterOrderRq.getBrokerId()) == null)
            errors.add(Message.UNKNOWN_BROKER_ID);
        if (shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()) == null)
            errors.add(Message.UNKNOWN_SHAREHOLDER_ID);
    }

    private void validateDeleteOrderRq(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (deleteOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin()) == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    private void validateAuctionStateRules(EnterOrderRq enterOrderRq, Security security) throws InvalidRequestException {
        if (security.getState() == MatchingState.AUCTION) {
            if (enterOrderRq.getMinimumExecutionQuantity() > 0 && enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
                throw new InvalidRequestException(Message.MIN_EXECUTION_QUANTITY_IN_AUCTION);
            if (enterOrderRq.getStopPrice() != 0 & enterOrderRq.getRequestType()==OrderEntryType.NEW_ORDER)
                throw new InvalidRequestException(Message.NEW_STOP_ORDER_IS_NOT_ALLOWED_IN_AUCTION);
            if (enterOrderRq.getStopPrice() != 0 & enterOrderRq.getRequestType()==OrderEntryType.UPDATE_ORDER)
                throw new InvalidRequestException(Message.UPDATE_STOP_ORDER_IS_NOT_ALLOWED_IN_AUCTION);
        }
    }

    private void publishEnterOrderReqEvents(MatchResult matchResult, EnterOrderRq enterOrderRq) {
        if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
            return;
        }
        if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_POSITIONS) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
            return;
        }
        if (matchResult.outcome() == MatchingOutcome.MINIMUM_NOT_MATCHED) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.MINIMUM_EXECUTION_QUANTITY_NOT_MATCHED)));
            return;
        }

        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            eventPublisher.publish(new OrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
        else
            eventPublisher.publish(new OrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));

        if (matchResult.outcome() == MatchingOutcome.NOT_ACTIVATED && enterOrderRq.getStopPrice() != 0)
            return;

        if ((matchResult.outcome() == MatchingOutcome.EXECUTED && matchResult.remainder() != null)  && enterOrderRq.getStopPrice() != 0)
            eventPublisher.publish(new OrderActivatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));

        if (!matchResult.trades().isEmpty())
            eventPublisher.publish(new OrderExecutedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
    }

    void publishOpenPriceEvent(Security security) {
        if (security.getState() == MatchingState.CONTINUOUS)
            return;
        CustomPair pair = security.findOpeningPrice();
        eventPublisher.publish(new OpeningPriceEvent(security.getIsin(), pair.getFirst(), pair.getSecond()));
    }
}
