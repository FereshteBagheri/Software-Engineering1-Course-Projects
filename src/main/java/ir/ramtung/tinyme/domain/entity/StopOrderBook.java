package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@Getter
public class StopOrderBook {
    private final LinkedList<StopLimitOrder> buyQueue;
    private final LinkedList<StopLimitOrder> sellQueue;

    public StopOrderBook() {
        buyQueue = new LinkedList<>();
        sellQueue = new LinkedList<>();
    }

    public void enqueue(StopLimitOrder order) {
        List<StopLimitOrder> queue = getQueue(order.getSide());
        ListIterator<StopLimitOrder> it = queue.listIterator();
        while (it.hasNext()) {
            if (order.queuesBefore(it.next())) {
                it.previous();
                break;
            }
        }
        order.queue();
        it.add(order);
    }

    private LinkedList<StopLimitOrder> getQueue(Side side) {
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

    public LinkedList<StopLimitOrder> getActivatableOrders(int lastTradePrice, Side side) {
        LinkedList<StopLimitOrder> activatableOrders = new LinkedList<StopLimitOrder>();
        LinkedList<StopLimitOrder> queue = getQueue(side);
        queue.removeIf(order -> {
            if (order.shouldActivate(lastTradePrice)) {
                activatableOrders.add(order);
                return true;
            }
            return false;
        });
        return activatableOrders;
    }

    public int totalSellQuantityByShareholder(Shareholder shareholder) {
        return sellQueue.stream()
                .filter(order -> order.getShareholder().equals(shareholder))
                .mapToInt(Order::getTotalQuantity)
                .sum();
    }
}


