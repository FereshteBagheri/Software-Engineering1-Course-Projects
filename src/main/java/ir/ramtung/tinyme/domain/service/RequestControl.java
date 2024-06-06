package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.*;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Component
public class RequestControl {
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;

    public RequestControl(SecurityRepository securityRepository, BrokerRepository brokerRepository,
            ShareholderRepository shareholderRepository) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
    }

    protected void validateRequest(ChangeMatchingStateRq request) throws InvalidRequestException {
        validateSecurity(request.getSecurityIsin());
    };

    private void validateSecurity(String isin) throws InvalidRequestException {
        Security security = securityRepository.findSecurityByIsin(isin);
        if (security == null)
            throw new InvalidRequestException(Message.UNKNOWN_SECURITY_ISIN);
    }

    protected void validateRequest(EnterOrderRq request) throws InvalidRequestException {
        validateEnterOrderRq(request);
        validateAuctionStateRules(request, securityRepository.findSecurityByIsin(request.getSecurityIsin()));
    };

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
        if (enterOrderRq.getStopPrice() != 0 && enterOrderRq.getPeakSize() != 0)
            errors.add(Message.INVALID_STOP_LIMIT_ORDER_WITH_PEAKSIZE);
        if (enterOrderRq.getMinimumExecutionQuantity() < 0
                || (enterOrderRq.getMinimumExecutionQuantity() > enterOrderRq.getQuantity()
                        && enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER))
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

    private void validateAuctionStateRules(EnterOrderRq enterOrderRq, Security security)
            throws InvalidRequestException {
        if (security.getState() == MatchingState.AUCTION) {
            if (enterOrderRq.getMinimumExecutionQuantity() > 0
                    && enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
                throw new InvalidRequestException(Message.MIN_EXECUTION_QUANTITY_IN_AUCTION);
            if (enterOrderRq.getStopPrice() != 0 & enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
                throw new InvalidRequestException(Message.NEW_STOP_ORDER_IS_NOT_ALLOWED_IN_AUCTION);
            if (enterOrderRq.getStopPrice() != 0 & enterOrderRq.getRequestType() == OrderEntryType.UPDATE_ORDER)
                throw new InvalidRequestException(Message.UPDATE_STOP_ORDER_IS_NOT_ALLOWED_IN_AUCTION);
        }
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

    protected void validateRequest(DeleteOrderRq request) throws InvalidRequestException {
        validateDeleteOrderRq(request);
    };

}
