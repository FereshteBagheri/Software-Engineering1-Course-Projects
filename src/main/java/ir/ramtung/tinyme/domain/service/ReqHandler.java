package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.repository.SecurityRepository;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.*;

public abstract class ReqHandler {
    SecurityRepository securityRepository;
    EventPublisher eventPublisher;
    ContinuousMatcher continuousMatcher;
    AuctionMatcher auctionMatcher;

    protected void validateRequest(EnterOrderRq request) throws InvalidRequestException{};
    protected void validateRequest(DeleteOrderRq request) throws InvalidRequestException{};
    protected void validateRequest(ChangeMatchingStateRq request) throws InvalidRequestException{};
    protected void processRequest(EnterOrderRq request)throws InvalidRequestException{};
    protected void processRequest(DeleteOrderRq request){};
    protected void processRequest(ChangeMatchingStateRq request){};

    private  void validateRequest(Request request) throws InvalidRequestException{   if (request instanceof EnterOrderRq) {
        validateRequest((EnterOrderRq) request);
    } else if (request instanceof DeleteOrderRq) {
        validateRequest((DeleteOrderRq) request);
    } else if (request instanceof ChangeMatchingStateRq) {
        validateRequest((ChangeMatchingStateRq) request);
    }}

    private void processRequest(Request request)throws InvalidRequestException{    if (request instanceof EnterOrderRq) {
        processRequest((EnterOrderRq) request);
    } else if (request instanceof DeleteOrderRq) {
        processRequest((DeleteOrderRq) request);
    } else if (request instanceof ChangeMatchingStateRq) {
        processRequest((ChangeMatchingStateRq) request);
    }}

    public final void handleRequest(Request request) {
        try {
            validateRequest(request);
            processRequest(request);
        } catch (InvalidRequestException ex) {
            handleInvalidRequest(request,ex);
        }
    }

    protected void handleInvalidRequest(Request request,InvalidRequestException ex) {

    }
}

