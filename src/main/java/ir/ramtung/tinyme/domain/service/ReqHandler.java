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
    RequestControl requestControl;

    protected void processRequest(EnterOrderRq request) throws InvalidRequestException{};
    protected void processRequest(DeleteOrderRq request) throws InvalidRequestException{};
    protected void processRequest(ChangeMatchingStateRq request){};
    protected void handleInvalidRequest(Request request,InvalidRequestException ex) {}

    private  void validateRequest(Request request) throws InvalidRequestException{   if (request instanceof EnterOrderRq) {
        requestControl.validateRequest((EnterOrderRq) request);
    } else if (request instanceof DeleteOrderRq) {
        requestControl.validateRequest((DeleteOrderRq) request);
    } else if (request instanceof ChangeMatchingStateRq) {
        requestControl.validateRequest((ChangeMatchingStateRq) request);
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

}

