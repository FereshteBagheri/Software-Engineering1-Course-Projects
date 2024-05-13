package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import lombok.Builder;
import lombok.Getter;
import java.util.LinkedList;
import java.util.List;

@Getter
@Builder
public class Security {
    private String isin;
    @Builder.Default
    private int tickSize = 1;
    @Builder.Default
    private int lotSize = 1;
    @Builder.Default
    private OrderBook orderBook = new OrderBook();
    @Builder.Default
    private StopOrderBook stopOrderBook = new StopOrderBook();
    @Builder.Default
    private MatchingState state = MatchingState.CONTINUOUS;
    private int lastTradePrice;

    public MatchResult newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        if (enterOrderRq.getSide() == Side.SELL &&
                !shareholder.hasEnoughPositionsOn(this,
                        orderBook.totalSellQuantityByShareholder(shareholder) + stopOrderBook.totalSellQuantityByShareholder(shareholder) +  enterOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions();
        Order order;
        if (enterOrderRq.getStopPrice() != 0)
            order = new StopLimitOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getStopPrice(), enterOrderRq.getOrderId());
        else if (enterOrderRq.getPeakSize() == 0)
            order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getMinimumExecutionQuantity(), false);
        else
            order = new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(),
                    enterOrderRq.getMinimumExecutionQuantity(), false);

        return matcher.execute(order);
    }

    public void deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        if (order == null)
            order = stopOrderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue()); 
        removeOrderByOrderId(order, deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
    }

    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        Order order;
        if (updateOrderRq.getStopPrice() != 0)
            order = stopOrderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        else
            order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if ((order instanceof IcebergOrder) && updateOrderRq.getPeakSize() == 0)
            throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
        if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0)
            throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);
        if (order.getMinimumExecutionQuantity() != updateOrderRq.getMinimumExecutionQuantity())
            throw new InvalidRequestException(Message.CANNOT_MODIFY_MINIMUM_EXECUTION_QUANTITY);

        if (updateOrderRq.getSide() == Side.SELL &&
                !order.getShareholder().hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(order.getShareholder()) +
                stopOrderBook.totalSellQuantityByShareholder(order.getShareholder())  - order.getQuantity() + updateOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions();

        boolean losesPriority = order.isQuantityIncreased(updateOrderRq.getQuantity())
                || updateOrderRq.getPrice() != order.getPrice()
                || ((order instanceof IcebergOrder icebergOrder) && (icebergOrder.getPeakSize() < updateOrderRq.getPeakSize()))
                || ((order instanceof StopLimitOrder stopLimitOrder) && (stopLimitOrder.getStopPrice() != updateOrderRq.getStopPrice()));

        if (updateOrderRq.getSide() == Side.BUY) {
            order.getBroker().increaseCreditBy(order.getValue());
        }
        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);
        if (!losesPriority) {
            if (updateOrderRq.getSide() == Side.BUY) {
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            return MatchResult.executed(null, List.of());
        }
        else
            order.markAsNew();

        removeOrderByOrderId(order, updateOrderRq.getSide(), updateOrderRq.getOrderId());

        MatchResult matchResult = matcher.execute(order);
        if (matchResult.outcome() != MatchingOutcome.EXECUTED && matchResult.outcome() != MatchingOutcome.NOT_ACTIVATED) {
            enqueueOrder(originalOrder);
            if (updateOrderRq.getSide() == Side.BUY) {
                originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
            }
        }
        return matchResult;
    }

    public LinkedList<StopLimitOrder> findTriggeredOrders(int last_Trade_Price) {
        LinkedList<StopLimitOrder> activeOrders = new LinkedList<StopLimitOrder>();
        if (last_Trade_Price > this.lastTradePrice)
            activeOrders = stopOrderBook.findTriggeredOrders(last_Trade_Price, Side.BUY);
        else 
            activeOrders = stopOrderBook.findTriggeredOrders(last_Trade_Price, Side.SELL);

        return activeOrders;
    }

    public void enqueueOrder(Order order) {
        if (order instanceof StopLimitOrder stopLimitOrder)
            stopOrderBook.enqueue(stopLimitOrder);
        else
            orderBook.enqueue(order);
    }

    public void removeOrderByOrderId(Order order, Side side, long orderId) {
        if (order instanceof StopLimitOrder)
            stopOrderBook.removeByOrderId(side, orderId);
        else
            orderBook.removeByOrderId(side, orderId);
    }

    public void setLastTradePrice(int last_Trade_Price) {
        this.lastTradePrice = last_Trade_Price;
    }

    public LinkedList<Order> findOpenOrders(int openingPrice, Side side) {
        return orderBook.findOpenOrders(openingPrice, side);
    }

    public void setMatchingState(MatchingState targetState) {
        this.state = targetState;
    }
    
    public CustomPair exchangedQuantity(int buyPrice, LinkedList<Order> sellQueue) {
        int exchangedQuantityValue = 0;
        int maxSellPrice = 0;

        for (Order sellOrder : sellQueue) {
            if (sellOrder.getPrice() <= buyPrice) {
                exchangedQuantityValue += sellOrder.getQuantity();
                maxSellPrice = sellOrder.getPrice();
            } else {
                break;
            }
        }

        return new CustomPair(exchangedQuantityValue, maxSellPrice);
    }

    public CustomPair findOpeningPrice() {
        int openingPrice = lastTradePrice;
        int maxExchangedQuantity = 0;
        int maxSellPrice = 0;
        int exchangedQuantityValueSideBuy = 0;

        LinkedList<Order> buyQueue = orderBook.getBuyQueue();
        LinkedList<Order> sellQueue = orderBook.getSellQueue();

        for (Order buyOrder : buyQueue) {
            exchangedQuantityValueSideBuy += buyOrder.getQuantity();
            CustomPair pair = exchangedQuantity(buyOrder.getPrice(), sellQueue);
            int exchangedQuantityValueSideSell = pair.getFirst();
            int exchangedQuantityValue = Math.min(exchangedQuantityValueSideSell, exchangedQuantityValueSideBuy);
            int sellPrice = pair.getSecond();

            if (exchangedQuantityValue > maxExchangedQuantity
                    || (exchangedQuantityValue == maxExchangedQuantity
                    && Math.abs(lastTradePrice - openingPrice) >= Math.abs(lastTradePrice - buyOrder.getPrice()))) {
                openingPrice = buyOrder.getPrice();
                maxExchangedQuantity = exchangedQuantityValue;
                maxSellPrice = sellPrice;
            }
        }

        if (Math.abs(lastTradePrice - openingPrice) >= Math.abs(lastTradePrice - maxSellPrice)) {
            openingPrice = maxSellPrice;
        }

        return new CustomPair(openingPrice, maxExchangedQuantity);
    }
    
}