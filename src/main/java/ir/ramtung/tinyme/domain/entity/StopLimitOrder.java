package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)

public class StopLimitOrder extends Order {
    int stopPrice;
    long requestId;
    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, int stopPrice, OrderStatus status, long requestId) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status);
        this.stopPrice = stopPrice;
        this.requestId = requestId;
    }

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, int stopPrice, long requestId) {
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, stopPrice, OrderStatus.NEW, requestId);
    }

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, int stopPrice, long requestId) {
        super(orderId, security, side, quantity, price, broker, shareholder);
        this.stopPrice = stopPrice;
        this.requestId = requestId;
    }


    // we can ignore snapshot functions for this type of order
    @Override
    public Order snapshot() {
        return new StopLimitOrder(orderId, security, side, quantity, price, broker, shareholder, entryTime, stopPrice, OrderStatus.SNAPSHOT, requestId);
    }

    @Override
    public Order snapshotWithQuantity(int newQuantity) {
        return new StopLimitOrder(orderId, security, side, newQuantity, price, broker, shareholder, entryTime, stopPrice, OrderStatus.SNAPSHOT, requestId) ;
    }

    @Override
    public void updateFromRequest(EnterOrderRq updateOrderRq) {
        super.updateFromRequest(updateOrderRq);
        stopPrice = updateOrderRq.getStopPrice();
        requestId = updateOrderRq.getRequestId();
    }

    @Override
    public boolean queuesBefore(Order order) {
        StopLimitOrder stopOrder = (StopLimitOrder) order;
        if (order.getSide() == Side.BUY)
            return stopPrice < stopOrder.getStopPrice();
        else
            return stopPrice > stopOrder.getStopPrice();
    }


    public Order active() {
        return new Order(orderId, security, side, quantity, price, broker, shareholder);
    }

    public boolean isTriggered(int lastTradePrice) {
        if (side == Side.BUY)
            return lastTradePrice >= stopPrice;
        else
            return lastTradePrice <= stopPrice;
    }
    
}

