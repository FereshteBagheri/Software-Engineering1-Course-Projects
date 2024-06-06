package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@Getter
public class OrderBook {
    private final LinkedList<Order> buyQueue;
    private final LinkedList<Order> sellQueue;

    public OrderBook() {
        buyQueue = new LinkedList<>();
        sellQueue = new LinkedList<>();
    }

    public void enqueue(Order order) {
        List<Order> queue = getQueue(order.getSide());
        ListIterator<Order> it = queue.listIterator();
        while (it.hasNext()) {
            if (order.queuesBefore(it.next())) {
                it.previous();
                break;
            }
        }
        order.queue();
        it.add(order);
    }

    private LinkedList<Order> getQueue(Side side) {
        return side == Side.BUY ? buyQueue : sellQueue;
    }

    public Order findByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        for (Order order : queue) {
            if (order.getOrderId() == orderId)
                return order;
        }
        return null;
    }

    public boolean removeByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        var it = queue.listIterator();
        while (it.hasNext()) {
            if (it.next().getOrderId() == orderId) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public Order matchWithFirst(Order newOrder) {
        var queue = getQueue(newOrder.getSide().opposite());
        if (newOrder.matches(queue.getFirst()))
            return queue.getFirst();
        else
            return null;
    }

    public void putBack(Order order) {
        LinkedList<Order> queue = getQueue(order.getSide());
        order.queue();
        queue.addFirst(order);
    }

    public void restoreSellOrder(Order sellOrder) {
        removeByOrderId(Side.SELL, sellOrder.getOrderId());
        putBack(sellOrder);
    }

    public void restoreBuyOrder(Order buyOrder) {
        removeByOrderId(Side.BUY, buyOrder.getOrderId());
        putBack(buyOrder);
    }

    public boolean hasOrderOfType(Side side) {
        return !getQueue(side).isEmpty();
    }

    public void removeFirst(Side side) {
        getQueue(side).removeFirst();
    }

    public int totalSellQuantityByShareholder(Shareholder shareholder) {
        return sellQueue.stream()
                .filter(order -> order.getShareholder().equals(shareholder))
                .mapToInt(Order::getTotalQuantity)
                .sum();
    }

    public LinkedList<Order> findOpenOrders(int openingPrice, Side side) {
        LinkedList<Order> orders = new LinkedList<>();
        LinkedList<Order> queue = this.getQueue(side);
        for (Order order : queue) {
            if (order.getSide() == Side.BUY && order.getPrice() >= openingPrice)
                orders.add(order);
            if (order.getSide() == Side.SELL && order.getPrice() <= openingPrice)
                orders.add(order);
        }
        return orders;
    }

    public CustomPair calculateTradeableQuantityAndSellPrice(int buyPrice, int lastTradePrice,
            int maxTradeableQuantityBuyPrice) {
        int maxFulfilledSellQuantity = 0;
        int nearestSellPrice = Integer.MIN_VALUE;

        for (Order sellOrder : sellQueue) {
            if (sellOrder.getPrice() <= buyPrice) {
                if (shouldUpdateNearestSellPrice(maxFulfilledSellQuantity, maxTradeableQuantityBuyPrice, lastTradePrice,
                        nearestSellPrice, sellOrder)) {
                    nearestSellPrice = sellOrder.getPrice();
                }
                maxFulfilledSellQuantity += sellOrder.getTotalQuantity();
            } else {
                break;
            }
        }

        return new CustomPair(maxFulfilledSellQuantity, nearestSellPrice);
    }

    public CustomPair findOpeningPrice(int lastTradePrice) {
        int openingPrice = 0;
        int tradeableQuantity = 0;
        int maxFulfilledSellPriceByBuy = 0;
        int maxTradeableQuantityBuyPrice = 0;

        for (Order buyOrder : buyQueue) {
            maxTradeableQuantityBuyPrice += buyOrder.getTotalQuantity();
            CustomPair tradeablePair = calculateTradeableQuantityAndSellPrice(buyOrder.getPrice(), lastTradePrice,
                    maxTradeableQuantityBuyPrice);

            int maxSellQuantityForBuyPrice = tradeablePair.getFirst();
            int exchangedQuantityValue = Math.min(maxSellQuantityForBuyPrice, maxTradeableQuantityBuyPrice);
            int sellPrice = tradeablePair.getSecond();

            if (exchangedQuantityValue > tradeableQuantity) {
                openingPrice = buyOrder.getPrice();
                tradeableQuantity = exchangedQuantityValue;
                maxFulfilledSellPriceByBuy = sellPrice;
            } else if (exchangedQuantityValue == tradeableQuantity) {
                openingPrice = getOptimalOpeningPrice(lastTradePrice, openingPrice, buyOrder.getPrice());
                maxFulfilledSellPriceByBuy = getOptimalOpeningPrice(lastTradePrice, openingPrice, sellPrice);
            }
        }

        openingPrice = finalizeOpeningPrice(lastTradePrice, openingPrice, maxFulfilledSellPriceByBuy,
                tradeableQuantity);

        return new CustomPair(openingPrice, tradeableQuantity);
    }

    private int getOptimalOpeningPrice(int lastTradePrice, int currentOpeningPrice, int newPrice) {
        if (Math.abs(lastTradePrice - currentOpeningPrice) >= Math.abs(lastTradePrice - newPrice)) {
            return newPrice;
        }
        return currentOpeningPrice;
    }

    private int finalizeOpeningPrice(int lastTradePrice, int openingPrice, int maxFulfilledSellPriceByBuy,
            int tradeableQuantity) {
        if (tradeableQuantity == 0) {
            return 0;
        }

        if (openingPrice >= lastTradePrice && lastTradePrice >= maxFulfilledSellPriceByBuy) {
            return lastTradePrice;
        } else if (Math.abs(lastTradePrice - openingPrice) >= Math.abs(lastTradePrice - maxFulfilledSellPriceByBuy)) {
            return maxFulfilledSellPriceByBuy;
        }

        return openingPrice;
    }

    private boolean shouldUpdateNearestSellPrice(int maxFulfilledSellQuantity, int maxTradeableQuantityBuyPrice,
            int lastTradePrice, int nearestSellPrice, Order sellOrder) {
        return maxFulfilledSellQuantity < maxTradeableQuantityBuyPrice ||
                Math.abs(lastTradePrice - nearestSellPrice) > Math.abs(lastTradePrice - sellOrder.getPrice());
    }
}
