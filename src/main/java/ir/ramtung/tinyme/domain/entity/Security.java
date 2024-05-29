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
        if (!shareholderHasEnoughPosition(enterOrderRq, shareholder, enterOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions();
        Order order = createOrderFromRequest(enterOrderRq, broker, shareholder);
        return matcher.execute(order);
    }

    public void deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = findOrderByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        validateDeleteOrderRequest(order);
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

        validateUpdateOrderRequest(order, updateOrderRq);

        if (!shareholderHasEnoughPosition(updateOrderRq, order.getShareholder(), updateOrderRq.getQuantity() - order.getQuantity()))
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
    
    public CustomPair TradeableQuantityBuyPrice(int buyPrice, LinkedList<Order> sellQueue, int maxTradeableQuantityBuyPrice) {
        int maxFulfilledSellQuantityByBuyPrice = 0;
        int nearestSellPrice = -1000000;

        for (Order sellOrder : sellQueue) {
            if (sellOrder.getPrice() <= buyPrice) {
                if (maxFulfilledSellQuantityByBuyPrice < maxTradeableQuantityBuyPrice ||
                    Math.abs(lastTradePrice - nearestSellPrice) > Math.abs(lastTradePrice - sellOrder.getPrice())) {
                    nearestSellPrice = sellOrder.getPrice();
                }
                maxFulfilledSellQuantityByBuyPrice += sellOrder.getTotalQuantity();
            } else {
                break;
            }
        }

        return new CustomPair(maxFulfilledSellQuantityByBuyPrice, nearestSellPrice);
    }

    public CustomPair findOpeningPrice() {
        int openingPrice = 0;
        int tradeableQuantity = 0;
        int maxFulfilledSellPriceByBuy = 0;
        int maxTradeableQuantityBuyPrice = 0;

        LinkedList<Order> buyQueue = orderBook.getBuyQueue();
        LinkedList<Order> sellQueue = orderBook.getSellQueue();
        for (Order buyOrder : buyQueue) {
            maxTradeableQuantityBuyPrice += buyOrder.getTotalQuantity();
            CustomPair pair = TradeableQuantityBuyPrice(buyOrder.getPrice(), sellQueue, maxTradeableQuantityBuyPrice);
            int maxSellQuantityForBuyPrice = pair.getFirst();
            int exchangedQuantityValue = Math.min(maxSellQuantityForBuyPrice, maxTradeableQuantityBuyPrice);
            int sellPrice = pair.getSecond();
            if (exchangedQuantityValue > tradeableQuantity){
                openingPrice = buyOrder.getPrice();
                tradeableQuantity = exchangedQuantityValue;
                maxFulfilledSellPriceByBuy = sellPrice;
            }else if (exchangedQuantityValue == tradeableQuantity) {
                if (Math.abs(lastTradePrice - openingPrice) >= Math.abs(lastTradePrice - buyOrder.getPrice()))
                    openingPrice = buyOrder.getPrice();
                if (Math.abs(lastTradePrice - openingPrice) >= Math.abs(lastTradePrice - sellPrice))
                    maxFulfilledSellPriceByBuy = sellPrice;
            }
        }
        if (openingPrice >= lastTradePrice && lastTradePrice >= maxFulfilledSellPriceByBuy){
            openingPrice = lastTradePrice;
        }else if (Math.abs(lastTradePrice - openingPrice) >= Math.abs(lastTradePrice - maxFulfilledSellPriceByBuy)) {
            openingPrice = maxFulfilledSellPriceByBuy;
        }
        if (tradeableQuantity == 0)
            openingPrice = 0;
        return new CustomPair(openingPrice, tradeableQuantity);
    }

    private boolean shareholderHasEnoughPosition(EnterOrderRq req, Shareholder shareholder, int newQuantity) {
        if (req.getSide() == Side.SELL &&
                !shareholder.hasEnoughPositionsOn(this,
                        totalSellQuantityByShareholderInQueue(shareholder) + newQuantity))
            return false;
        return true;
    }

    private int totalSellQuantityByShareholderInQueue(Shareholder shareholder){
        return orderBook.totalSellQuantityByShareholder(shareholder) + stopOrderBook.totalSellQuantityByShareholder(shareholder);
    }

    private Order createOrderFromRequest(EnterOrderRq request, Broker broker, Shareholder shareholder) {
        if (request.getStopPrice() != 0)
            return new StopLimitOrder(request.getOrderId(), this, request.getSide(),
                    request.getQuantity(), request.getPrice(), broker, shareholder,
                    request.getEntryTime(), request.getStopPrice(), request.getRequestId());
        else if (request.getPeakSize() == 0)
            return new Order(request.getOrderId(), this, request.getSide(),
                    request.getQuantity(), request.getPrice(), broker, shareholder,
                    request.getEntryTime(), request.getMinimumExecutionQuantity(), false);
        else
            return new IcebergOrder(request.getOrderId(), this, request.getSide(),
                    request.getQuantity(), request.getPrice(), broker, shareholder,
                    request.getEntryTime(), request.getPeakSize(),
                    request.getMinimumExecutionQuantity(), false);
    }

    private Order findOrderByOrderId(Side side, long orderId) {
        Order order = orderBook.findByOrderId(side, orderId);
        if (order == null)
            order = stopOrderBook.findByOrderId(side, orderId);
        return order;
    }

    private void validateDeleteOrderRequest(Order order) throws InvalidRequestException {
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if (order instanceof StopLimitOrder && state == MatchingState.AUCTION)
            throw new InvalidRequestException(Message.DELETE_STOP_ORDER_NOT_ALLOWED_IN_AUCTION);

    }

    private void validateUpdateOrderRequest(Order order, EnterOrderRq updateOrderRq) throws InvalidRequestException {
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if ((order instanceof IcebergOrder) && updateOrderRq.getPeakSize() == 0)
            throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
        if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0)
            throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);
        if (order.getMinimumExecutionQuantity() != updateOrderRq.getMinimumExecutionQuantity())
            throw new InvalidRequestException(Message.CANNOT_MODIFY_MINIMUM_EXECUTION_QUANTITY);
    }
}