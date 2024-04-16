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

    public LinkedList<Order> getActivateOrders(int lastTradePrice) {
        LinkedList<Order> activOrders = new LinkedList<>();
    
        buyQueue.stream()
            .filter(order -> order instanceof StopLimitOrder)
            .map(order -> (StopLimitOrder) order)
            .takeWhile(stopLimitOrder -> stopLimitOrder.isActivated(lastTradePrice))
            .map(StopLimitOrder::active)
            .forEach(activOrders::add);
    
        sellQueue.stream()
            .filter(order -> order instanceof StopLimitOrder)
            .map(order -> (StopLimitOrder) order)
            .takeWhile(stopLimitOrder -> stopLimitOrder.isActivated(lastTradePrice))
            .map(StopLimitOrder::active)
            .forEach(activOrders::add);
    
        return activOrders;
    }
    
}
